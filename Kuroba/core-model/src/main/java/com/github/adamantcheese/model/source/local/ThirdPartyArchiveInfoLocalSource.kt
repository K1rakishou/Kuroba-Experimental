package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.entity.archive.LastUsedArchiveForThreadRelationEntity
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveFetchHistoryEntity
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveInfoEntity
import com.github.adamantcheese.model.source.cache.LastUsedArchiveForThreadCache
import org.joda.time.DateTime

class ThirdPartyArchiveInfoLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger,
        private val lastUsedArchiveForThreadCache: LastUsedArchiveForThreadCache
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag ThirdPartyArchiveInfoLocalSource"
    private val chanBoardDao = database.chanBoardDao()
    private val chanThreadDao = database.chanThreadDao()
    private val thirdPartyArchiveInfoDao = database.thirdPartyArchiveInfoDao()
    private val thirdPartyArchiveFetchHistoryDao = database.thirdPartyArchiveFetchHistoryDao()
    private val lastUsedArchiveForThreadDao = database.lastUsedArchiveForThreadDao()

    suspend fun insertThirdPartyArchiveInfo(thirdPartyArchiveInfo: ThirdPartyArchiveInfo): ThirdPartyArchiveInfo? {
        ensureInTransaction()

        val databaseId = thirdPartyArchiveInfoDao.insertOrUpdate(
                ThirdPartyArchiveInfoEntity(
                        archiveId = 0L,
                        archiveDomain = thirdPartyArchiveInfo.archiveDescriptor.domain,
                        enabled = thirdPartyArchiveInfo.enabled
                )
        )

        if (databaseId < 0L) {
            return null
        }

        return thirdPartyArchiveInfo.copy(databaseId = databaseId)
    }

    suspend fun selectLastUsedArchiveIdByThreadDescriptor(
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    ): Long? {
        val fromCache = lastUsedArchiveForThreadCache.get(threadDescriptor)
        if (fromCache != null) {
            return fromCache
        }

        val chanThreadId = getChanThreadIdOrNull(threadDescriptor)
          ?: return null

        return lastUsedArchiveForThreadDao.select(chanThreadId)?.thirdPartyArchiveInfoEntity?.archiveId
    }

    suspend fun selectThirdPartyArchiveInfo(archiveDescriptor: ArchiveDescriptor): ThirdPartyArchiveInfo? {
        ensureInTransaction()

        val entity = thirdPartyArchiveInfoDao.select(archiveDescriptor.domain)
                ?: return null

        return ThirdPartyArchiveInfo(
                entity.archiveId,
                archiveDescriptor,
                entity.enabled
        )
    }

    suspend fun insertFetchResult(fetchResult: ThirdPartyArchiveFetchResult): ThirdPartyArchiveFetchResult? {
        ensureInTransaction()
        require(fetchResult.databaseId == 0L) { "Bad fetchResult.databaseId: ${fetchResult.databaseId}" }

        val chanThreadId = getChanThreadIdOrNull(fetchResult.threadDescriptor)
                ?: return null

        val thirdPartyArchiveInfoEntity = thirdPartyArchiveInfoDao.select(
                fetchResult.archiveDescriptor.domain
        )

        checkNotNull(thirdPartyArchiveInfoEntity) {
            "ThirdPartyArchiveInfoEntity was not created for ${fetchResult.archiveDescriptor} beforehand!"
        }

        val databaseId = thirdPartyArchiveFetchHistoryDao.insert(
                ThirdPartyArchiveFetchHistoryEntity(
                        id = 0L,
                        ownerThirdPartyArchiveId = thirdPartyArchiveInfoEntity.archiveId,
                        ownerThreadId = chanThreadId,
                        success = fetchResult.success,
                        errorText = fetchResult.errorText,
                        insertedOn = fetchResult.insertedOn
                )
        )

        if (databaseId < 0L) {
            return null
        }

        lastUsedArchiveForThreadDao.insert(
          LastUsedArchiveForThreadRelationEntity(
            ownerThreadId = chanThreadId,
            archiveId = thirdPartyArchiveInfoEntity.archiveId
          )
        )

        lastUsedArchiveForThreadCache.store(
          fetchResult.threadDescriptor,
          thirdPartyArchiveInfoEntity.archiveId
        )

        return fetchResult.copy(databaseId = databaseId)
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptorList: List<ArchiveDescriptor>,
            newerThan: DateTime,
            count: Int
    ): Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>> {
        ensureInTransaction()

        val domainList = archiveDescriptorList.map { archiveDescriptor -> archiveDescriptor.domain }
        if (domainList.isEmpty()) {
            return emptyMap()
        }

        val thirdPartyArchiveInfoEntityList = thirdPartyArchiveInfoDao.selectMany(domainList)
        if (thirdPartyArchiveInfoEntityList.isEmpty()) {
            return emptyMap()
        }

        val fetchHistoryList = thirdPartyArchiveInfoEntityList.flatMap { thirdPartyArchiveInfoEntity ->
            return@flatMap thirdPartyArchiveFetchHistoryDao.selectLatest(
                    thirdPartyArchiveInfoEntity.archiveId,
                    newerThan,
                    count
            )
        }

        if (fetchHistoryList.isEmpty()) {
            return emptyMap()
        }

        // This thing is a little bit tricky because we need to get N latest fetch results overall,
        // but ThirdPartyArchiveFetchResult requires us to provide ThreadDescriptor as well, which
        // we don't have here. So we are forced to construct them manually by searching all the
        // necessary stuff in the database directly. This method is only called once upon the app
        // start up and then the results are cached in memory, so it should be fine.
        val chanThreadEntityMap = fetchHistoryList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .map { fetchHistoryList ->
                    fetchHistoryList.map { fetchHistory -> fetchHistory.ownerThreadId }
                }
                .flatMap { ownerThreadIdListChunk ->
                    chanThreadDao.selectManyByThreadIdList(ownerThreadIdListChunk)
                }
                .groupBy { chanThreadEntity -> chanThreadEntity.threadId }

        val chanBoardIdList = chanThreadEntityMap.values.flatMap { chanThreadEntityList ->
            return@flatMap chanThreadEntityList.map { chanThreadEntity -> chanThreadEntity.ownerBoardId }
        }.distinct()

        val chanBoardEntityMap = chanBoardIdList
                .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
                .flatMap { chanBoardIdListChunk -> chanBoardDao.selectMany(chanBoardIdListChunk) }
                .associateBy { chanThreadEntity -> chanThreadEntity.boardId }

        val archiveDescriptorMap = archiveDescriptorList
                .associateBy { archiveDescriptor -> archiveDescriptor.domain }
        val thirdPartyArchiveInfoEntityMap = thirdPartyArchiveInfoEntityList
                .associateBy { thirdPartyArchiveInfoEntity -> thirdPartyArchiveInfoEntity.archiveId }

        return fetchHistoryList
                .sortedByDescending { thirdPartyArchiveFetchHistoryEntity ->
                    return@sortedByDescending thirdPartyArchiveFetchHistoryEntity.insertedOn
                }
                .mapNotNull { thirdPartyArchiveFetchHistoryEntity ->
                    val archiveId = thirdPartyArchiveFetchHistoryEntity.ownerThirdPartyArchiveId
                    val threadId = thirdPartyArchiveFetchHistoryEntity.ownerThreadId
                    val domain = thirdPartyArchiveInfoEntityMap[archiveId]?.archiveDomain

                    val archiveDescriptor = archiveDescriptorMap[domain]
                            ?: return@mapNotNull null
                    val chanThreadEntity = chanThreadEntityMap[threadId]?.firstOrNull()
                            ?: return@mapNotNull null
                    val chanBoardEntity = chanBoardEntityMap[chanThreadEntity.ownerBoardId]
                            ?: return@mapNotNull null

                    val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
                            chanBoardEntity.siteName,
                            chanBoardEntity.boardCode,
                            chanThreadEntity.threadNo
                    )

                    return@mapNotNull ThirdPartyArchiveFetchResult(
                            databaseId = thirdPartyArchiveFetchHistoryEntity.id,
                            archiveDescriptor = archiveDescriptor,
                            threadDescriptor = threadDescriptor,
                            success = thirdPartyArchiveFetchHistoryEntity.success,
                            errorText = thirdPartyArchiveFetchHistoryEntity.errorText,
                            insertedOn = thirdPartyArchiveFetchHistoryEntity.insertedOn
                    )
                }
                .groupBy { fetchResult -> fetchResult.archiveDescriptor }
    }

    suspend fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult) {
        ensureInTransaction()

        val thirdPartyArchiveInfoEntity = thirdPartyArchiveInfoDao.select(
                fetchResult.archiveDescriptor.domain
        )

        checkNotNull(thirdPartyArchiveInfoEntity) {
            "ThirdPartyArchiveInfoEntity was not created for ${fetchResult.archiveDescriptor} beforehand!"
        }

        thirdPartyArchiveFetchHistoryDao.delete(fetchResult.databaseId)
    }

    suspend fun archiveExists(archiveDescriptor: ArchiveDescriptor): Boolean {
        ensureInTransaction()

        return thirdPartyArchiveInfoDao.select(archiveDescriptor.domain) != null
    }

    suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): Boolean {
        ensureInTransaction()

        return thirdPartyArchiveInfoDao.isArchiveEnabled(archiveDescriptor.domain)
    }

    suspend fun setArchiveEnabled(archiveDescriptor: ArchiveDescriptor, enabled: Boolean) {
        ensureInTransaction()

        thirdPartyArchiveInfoDao.updateArchiveEnabled(archiveDescriptor.domain, enabled)
    }

    suspend fun deleteOlderThan(archiveDescriptor: ArchiveDescriptor, fetchHistoryId: Long): Int {
        ensureInTransaction()

        val archiveId = thirdPartyArchiveInfoDao.select(archiveDescriptor.domain)?.archiveId
                ?: return 0

        return thirdPartyArchiveFetchHistoryDao.deleteOlderThan(archiveId, fetchHistoryId)
    }

    private suspend fun getChanThreadIdOrNull(threadDescriptor: ChanDescriptor.ThreadDescriptor): Long? {
        val chanBoardEntity = chanBoardDao.select(
                threadDescriptor.siteName(),
                threadDescriptor.boardCode()
        )

        if (chanBoardEntity == null) {
            return null
        }

        return chanThreadDao.select(
                chanBoardEntity.boardId,
                threadDescriptor.opNo
        )?.threadId
    }

}