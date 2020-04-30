package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveFetchHistoryEntity
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveInfoEntity
import org.joda.time.DateTime

class ThirdPartyArchiveInfoLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag ThirdPartyArchiveInfoLocalSource"
    private val chanBoardDao = database.chanBoardDao()
    private val chanThreadDao = database.chanThreadDao()
    private val thirdPartyArchiveInfoDao = database.thirdPartyArchiveInfoDao()
    private val thirdPartyArchiveFetchHistoryDao = database.thirdPartyArchiveFetchHistoryDao()

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

        val chanThreadId = getChanThreadIdOrNull(fetchResult)
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

        return fetchResult.copy(databaseId = databaseId)
    }

    private suspend fun getChanThreadIdOrNull(fetchResult: ThirdPartyArchiveFetchResult): Long? {
        val chanBoardEntity = chanBoardDao.select(
                fetchResult.threadDescriptor.siteName(),
                fetchResult.threadDescriptor.boardCode()
        )

        if (chanBoardEntity == null) {
            return null
        }

        return chanThreadDao.select(
                chanBoardEntity.boardId,
                fetchResult.threadDescriptor.opNo
        )?.threadId
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

    suspend fun setArchiveEnabled(archiveDescriptor: ArchiveDescriptor, enabled: Boolean) {
        ensureInTransaction()

        thirdPartyArchiveInfoDao.updateArchiveEnabled(archiveDescriptor.domain, enabled)
    }

    suspend fun deleteOld(olderThan: DateTime) {
        ensureInTransaction()

        thirdPartyArchiveFetchHistoryDao.deleteOlderThan(olderThan)
    }

}