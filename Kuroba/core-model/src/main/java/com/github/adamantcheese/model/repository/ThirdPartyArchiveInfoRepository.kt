package com.github.adamantcheese.model.repository

import android.util.Log
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.common.SuspendableInitializer
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
    private val suspendableInitializer = SuspendableInitializer<Unit>("${TAG}_SuspendableLazy<Unit>", false)

    suspend fun init(thirdPartyArchiveInfoList: List<ThirdPartyArchiveInfo>) {
        val result = withTransactionSafe {
            thirdPartyArchiveInfoList.forEach { thirdPartyArchiveInfo ->
                if (!localSource.archiveExists(thirdPartyArchiveInfo.archiveDescriptor)) {
                    localSource.insertThirdPartyArchiveInfo(thirdPartyArchiveInfo)
                }
            }

            Log.d(TAG, "withTransactionSafe end")
            return@withTransactionSafe
        }

        Log.d(TAG, "before initWithModularResult()")
        suspendableInitializer.initWithModularResult(result)
    }

    suspend fun insertFetchResult(
            fetchResult: ThirdPartyArchiveFetchResult
    ): ModularResult<ThirdPartyArchiveFetchResult?> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                require(fetchResult.databaseId == 0L) { "Bad fetchResult.databaseId: ${fetchResult.databaseId}" }
                deleteOld()

                return@withTransactionSafe localSource.insertFetchResult(fetchResult)
            }
        }
    }

    suspend fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult): ModularResult<Unit> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                require(fetchResult.databaseId > 0L) { "Bad fetchResult.databaseId: ${fetchResult.databaseId}" }
                localSource.deleteFetchResult(fetchResult)

                return@withTransactionSafe
            }
        }
    }

    suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): ModularResult<Boolean> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                return@withTransactionSafe localSource.isArchiveEnabled(archiveDescriptor)
            }
        }
    }

    suspend fun setArchiveEnabled(
            archiveDescriptor: ArchiveDescriptor,
            isEnabled: Boolean
    ): ModularResult<Unit> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                return@withTransactionSafe localSource.setArchiveEnabled(archiveDescriptor, isEnabled)
            }
        }
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptor: ArchiveDescriptor
    ): ModularResult<List<ThirdPartyArchiveFetchResult>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                return@withTransactionSafe localSource.selectLatestFetchHistory(
                        archiveDescriptor,
                        appConstants.archiveFetchHistoryMaxEntries
                )
            }
        }
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptorList: List<ArchiveDescriptor>
    ): ModularResult<Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                val resultMap = mutableMapOf<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>()

                archiveDescriptorList.forEach { archiveDescriptor ->
                    val history = localSource.selectLatestFetchHistory(
                            archiveDescriptor,
                            appConstants.archiveFetchHistoryMaxEntries
                    )

                    resultMap[archiveDescriptor] = history
                }

                return@withTransactionSafe resultMap.toMap()
            }
        }
    }

    fun fetchThreadFromNetworkBlocking(
            threadArchiveRequestLink: String,
            threadNo: Long,
            supportsMediaThumbnails: Boolean,
            supportsMedia: Boolean
    ): ModularResult<ArchivesRemoteSource.ArchiveThread> {
        // We don't need to use SuspendableInitializer here
        return runBlocking {
            return@runBlocking safeRun {
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