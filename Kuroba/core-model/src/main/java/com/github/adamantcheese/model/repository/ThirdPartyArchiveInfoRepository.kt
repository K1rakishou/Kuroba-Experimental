package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.source.local.ThirdPartyArchiveInfoLocalSource
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

class ThirdPartyArchiveInfoRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val localSource: ThirdPartyArchiveInfoLocalSource,
        private val remoteSource: ArchivesRemoteSource,
        private val appConstants: AppConstants
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag ThirdPartyArchiveInfoRepository"
    private val alreadyDeletedOld = AtomicBoolean(false)

    suspend fun insertThirdPartyArchiveInfo(
            thirdPartyArchiveInfo: ThirdPartyArchiveInfo
    ): ModularResult<Unit> {
        return withTransactionSafe {
            localSource.insertThirdPartyArchiveInfo(thirdPartyArchiveInfo)

            return@withTransactionSafe
        }
    }

    suspend fun insertFetchResult(
            fetchResult: ThirdPartyArchiveFetchResult
    ): ModularResult<ThirdPartyArchiveFetchResult?> {
        return withTransactionSafe {
            require(fetchResult.databaseId == 0L) { "Bad fetchResult.databaseId: ${fetchResult.databaseId}" }
            deleteOld()

            return@withTransactionSafe localSource.insertFetchResult(fetchResult)
        }
    }

    suspend fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult): ModularResult<Unit> {
        return withTransactionSafe {
            require(fetchResult.databaseId > 0L) { "Bad fetchResult.databaseId: ${fetchResult.databaseId}" }
            localSource.deleteFetchResult(fetchResult)

            return@withTransactionSafe
        }
    }

    suspend fun archiveExists(archiveDescriptor: ArchiveDescriptor): ModularResult<Boolean> {
        return withTransactionSafe {
            return@withTransactionSafe localSource.archiveExists(archiveDescriptor)
        }
    }

    suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): ModularResult<Boolean> {
        return withTransactionSafe {
            return@withTransactionSafe localSource.isArchiveEnabled(archiveDescriptor)
        }
    }

    suspend fun setArchiveEnabled(
            archiveDescriptor: ArchiveDescriptor,
            isEnabled: Boolean
    ): ModularResult<Unit> {
        return withTransactionSafe {
            return@withTransactionSafe localSource.setArchiveEnabled(archiveDescriptor, isEnabled)
        }
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptor: ArchiveDescriptor
    ): ModularResult<List<ThirdPartyArchiveFetchResult>> {
        return withTransactionSafe {
            return@withTransactionSafe localSource.selectLatestFetchHistory(
                    archiveDescriptor,
                    appConstants.archiveFetchHistoryMaxEntries
            )
        }
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptorList: List<ArchiveDescriptor>
    ): ModularResult<Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>> {
        return withTransactionSafe {
            val resultMap = mutableMapOf<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>()

            archiveDescriptorList.forEach { archiveDescriptor ->
                val history = localSource.selectLatestFetchHistory(
                        archiveDescriptor,
                        appConstants.archiveFetchHistoryMaxEntries
                )

                resultMap[archiveDescriptor] = history
            }

            return@withTransactionSafe resultMap
        }
    }

    fun fetchThreadFromNetworkBlocking(
            threadArchiveRequestLink: String,
            threadNo: Long,
            supportsMediaThumbnails: Boolean,
            supportsMedia: Boolean
    ): ModularResult<ArchivesRemoteSource.ArchiveThread> {
        return runBlocking {
            return@runBlocking ModularResult.safeRun {
                return@safeRun remoteSource.fetchThreadFromNetwork(
                        threadArchiveRequestLink,
                        threadNo,
                        supportsMediaThumbnails,
                        supportsMedia
                )
            }
        }
    }

    private suspend fun deleteOld() {
        if (!alreadyDeletedOld.compareAndSet(false, true)) {
            return
        }

        localSource.deleteOld()
    }

}