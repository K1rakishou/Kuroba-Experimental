package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveFetchHistoryEntity
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveInfoEntity
import org.joda.time.DateTime
import org.joda.time.Duration

class ThirdPartyArchiveInfoLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag ThirdPartyArchiveInfoLocalSource"
    private val thirdPartyArchiveInfoDao = database.thirdPartyArchiveInfoDao()
    private val thirdPartyArchiveFetchHistoryDao = database.thirdPartyArchiveFetchHistoryDao()

    suspend fun insertThirdPartyArchiveInfo(thirdPartyArchiveInfo: ThirdPartyArchiveInfo): Long {
        ensureInTransaction()

        return thirdPartyArchiveInfoDao.insertOrUpdate(
                ThirdPartyArchiveInfoEntity(
                        archiveId = 0L,
                        archiveDomain = thirdPartyArchiveInfo.archiveDescriptor.domain,
                        enabled = thirdPartyArchiveInfo.enabled
                )
        )
    }

    suspend fun insertFetchHistory(thirdPartyArchiveFetchResult: ThirdPartyArchiveFetchResult) {
        ensureInTransaction()

        val thirdPartyArchiveInfoEntity = thirdPartyArchiveInfoDao.select(
                thirdPartyArchiveFetchResult.archiveDescriptor.domain
        )

        checkNotNull(thirdPartyArchiveInfoEntity) {
            "ThirdPartyArchiveInfoEntity was not created for " +
                    "${thirdPartyArchiveFetchResult.archiveDescriptor} beforehand!"
        }

        thirdPartyArchiveFetchHistoryDao.insert(
                ThirdPartyArchiveFetchHistoryEntity(
                        id = 0L,
                        ownerThirdPartyArchiveId = thirdPartyArchiveInfoEntity.archiveId,
                        success = thirdPartyArchiveFetchResult.success,
                        errorText = thirdPartyArchiveFetchResult.errorText,
                        insertedOn = DateTime.now()
                )
        )
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptor: ArchiveDescriptor,
            count: Int
    ): List<ThirdPartyArchiveFetchResult> {
        ensureInTransaction()

        val thirdPartyArchiveInfoEntity = thirdPartyArchiveInfoDao.select(archiveDescriptor.domain)
                ?: return emptyList()

        val newerThan = DateTime.now().minus(THIRTY_MINUTES)

        return thirdPartyArchiveFetchHistoryDao.selectLatest(
                thirdPartyArchiveInfoEntity.archiveId,
                newerThan,
                count
        ).map { thirdPartyArchiveFetchHistoryEntity ->
            return@map ThirdPartyArchiveFetchResult(
                    archiveDescriptor = archiveDescriptor,
                    success = thirdPartyArchiveFetchHistoryEntity.success,
                    errorText = thirdPartyArchiveFetchHistoryEntity.errorText
            )
        }
    }

    suspend fun archiveExists(archiveDescriptor: ArchiveDescriptor): Boolean {
        ensureInTransaction()

        return thirdPartyArchiveInfoDao.select(archiveDescriptor.domain) != null
    }

    suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): Boolean {
        ensureInTransaction()

        return thirdPartyArchiveInfoDao.selectIsArchiveEnabled(archiveDescriptor.domain)
    }

    suspend fun setArchiveEnabled(archiveDescriptor: ArchiveDescriptor, enabled: Boolean) {
        ensureInTransaction()

        thirdPartyArchiveInfoDao.updateArchiveEnabled(archiveDescriptor.domain, enabled)
    }

    suspend fun deleteOld() {
        ensureInTransaction()

        val olderThan = DateTime.now().minus(ONE_HOUR)
        thirdPartyArchiveFetchHistoryDao.deleteOlderThan(olderThan)
    }

    companion object {
        private val THIRTY_MINUTES = Duration.standardMinutes(30)
        private val ONE_HOUR = Duration.standardHours(1)
    }
}