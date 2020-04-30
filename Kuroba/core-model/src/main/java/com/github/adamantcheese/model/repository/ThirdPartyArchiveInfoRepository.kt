package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.source.cache.ThirdPartyArchiveInfoCache
import com.github.adamantcheese.model.source.local.ThirdPartyArchiveInfoLocalSource
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import org.joda.time.DateTime
import org.joda.time.Duration
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
    private val suspendableInitializer = SuspendableInitializer<Unit>("${TAG}_init", false)
    private val thirdPartyArchiveInfoCache = ThirdPartyArchiveInfoCache()

    suspend fun init(allArchiveDescriptors: List<ArchiveDescriptor>) {
        val result = withTransactionSafe {
            val thirdPartyArchiveInfoList = allArchiveDescriptors.map { archiveDescriptor ->
                ThirdPartyArchiveInfo(
                        databaseId = 0L,
                        archiveDescriptor = archiveDescriptor,
                        enabled = false
                )
            }

            thirdPartyArchiveInfoList.forEach { thirdPartyArchiveInfo ->
                val archiveInfo = if (!localSource.archiveExists(thirdPartyArchiveInfo.archiveDescriptor)) {
                    val insertedIntoDatabase = localSource.insertThirdPartyArchiveInfo(
                            thirdPartyArchiveInfo
                    )

                    requireNotNull(insertedIntoDatabase) {
                        "Couldn't insert archive info into the database, " +
                                "archiveDescriptor = ${thirdPartyArchiveInfo.archiveDescriptor}"
                    }
                } else {
                    val fromDatabase = localSource.selectThirdPartyArchiveInfo(
                            thirdPartyArchiveInfo.archiveDescriptor
                    )

                    requireNotNull(fromDatabase) {
                        "Couldn't find archive info in the database, " +
                                "archiveDescriptor = ${thirdPartyArchiveInfo.archiveDescriptor}"
                    }
                }

                thirdPartyArchiveInfoCache.putThirdPartyArchiveInfo(archiveInfo)
            }

            logger.log(TAG, "Loaded ${thirdPartyArchiveInfoList.size} archives")
            return@withTransactionSafe
        }

        suspendableInitializer.initWithModularResult(result)
    }

    suspend fun insertFetchResult(
            fetchResult: ThirdPartyArchiveFetchResult
    ): ModularResult<ThirdPartyArchiveFetchResult?> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                require(fetchResult.databaseId == 0L) {
                    "Bad fetchResult.databaseId: ${fetchResult.databaseId}"
                }
                deleteOld()

                val thirdPartyArchiveFetchResult = localSource.insertFetchResult(fetchResult)
                if (thirdPartyArchiveFetchResult != null) {
                    thirdPartyArchiveInfoCache.putThirdPartyArchiveFetchResult(
                            thirdPartyArchiveFetchResult
                    )
                }

                return@withTransactionSafe thirdPartyArchiveFetchResult
            }
        }
    }

    suspend fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult): ModularResult<Unit> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                require(fetchResult.databaseId > 0L) {
                    "Bad fetchResult.databaseId: ${fetchResult.databaseId}"
                }

                localSource.deleteFetchResult(fetchResult)
                thirdPartyArchiveInfoCache.deleteFetchResult(fetchResult)

                return@withTransactionSafe
            }
        }
    }

    suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): ModularResult<Boolean> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized safeRun {
                return@safeRun thirdPartyArchiveInfoCache.isArchiveEnabled(archiveDescriptor)
            }
        }
    }

    suspend fun setArchiveEnabled(
            archiveDescriptor: ArchiveDescriptor,
            isEnabled: Boolean
    ): ModularResult<Unit> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized withTransactionSafe {
                localSource.setArchiveEnabled(archiveDescriptor, isEnabled)
                thirdPartyArchiveInfoCache.setArchiveEnabled(archiveDescriptor, isEnabled)

                return@withTransactionSafe
            }
        }
    }

    suspend fun selectLatestFetchHistoryForThread(
            archiveDescriptorList: List<ArchiveDescriptor>,
            threadDescriptor: ChanDescriptor.ThreadDescriptor
    ): ModularResult<Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized safeRun {
                val resultMap = mutableMapOf<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>()
                val newerThan = DateTime.now().minus(THIRTY_MINUTES)

                archiveDescriptorList.forEach { archiveDescriptor ->
                    val threadArchiveFetchHistory = thirdPartyArchiveInfoCache.selectLatestFetchHistoryForThread(
                            archiveDescriptor,
                            threadDescriptor,
                            newerThan,
                            appConstants.archiveFetchHistoryMaxEntries
                    )

                    resultMap[archiveDescriptor] = threadArchiveFetchHistory
                }

                return@safeRun resultMap.toMap()
            }
        }
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptor: ArchiveDescriptor
    ): ModularResult<List<ThirdPartyArchiveFetchResult>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized safeRun {
                val newerThan = DateTime.now().minus(THIRTY_MINUTES)

                return@safeRun thirdPartyArchiveInfoCache.selectLatestFetchHistory(
                        archiveDescriptor,
                        newerThan,
                        appConstants.archiveFetchHistoryMaxEntries
                )
            }
        }
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptorList: List<ArchiveDescriptor>
    ): ModularResult<Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized safeRun {
                val resultMap = mutableMapOf<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>()
                val newerThan = DateTime.now().minus(THIRTY_MINUTES)

                archiveDescriptorList.forEach { archiveDescriptor ->
                    val history = thirdPartyArchiveInfoCache.selectLatestFetchHistory(
                            archiveDescriptor,
                            newerThan,
                            appConstants.archiveFetchHistoryMaxEntries
                    )

                    resultMap[archiveDescriptor] = history
                }

                return@safeRun resultMap.toMap()
            }
        }
    }

    suspend fun fetchThreadFromNetwork(
            threadArchiveRequestLink: String,
            threadNo: Long,
            supportsMediaThumbnails: Boolean,
            supportsMedia: Boolean
    ): ModularResult<ArchivesRemoteSource.ArchiveThread> {
        // We don't need to use SuspendableInitializer here
        return safeRun {
            return@safeRun remoteSource.fetchThreadFromNetwork(
                    threadArchiveRequestLink,
                    threadNo,
                    supportsMediaThumbnails,
                    supportsMedia
            )
        }
    }

    private suspend fun deleteOld() {
        if (!alreadyDeletedOld.compareAndSet(false, true)) {
            return
        }

        require(isInTransaction()) { "Must be called in transaction!" }

        val olderThan = DateTime.now().minus(ONE_HOUR)
        localSource.deleteOld(olderThan)
        thirdPartyArchiveInfoCache.deleteOld(olderThan)
    }

    companion object {
        private val ONE_HOUR = Duration.standardHours(1)
        private val THIRTY_MINUTES = Duration.standardMinutes(30)
    }
}