package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
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

class ThirdPartyArchiveInfoRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val localSource: ThirdPartyArchiveInfoLocalSource,
        private val remoteSource: ArchivesRemoteSource,
        private val appConstants: AppConstants
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag ThirdPartyArchiveInfoRepository"
    private val suspendableInitializer = SuspendableInitializer<Unit>("${TAG}_init", false)
    private val thirdPartyArchiveInfoCache = ThirdPartyArchiveInfoCache()

    suspend fun init(allArchiveDescriptors: List<ArchiveDescriptor>): Map<String, ThirdPartyArchiveInfo> {
        val result = tryWithTransaction {
            val resultList = mutableListOf<ThirdPartyArchiveInfo>()

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

                require(archiveInfo.databaseId > 0L) {
                    "Bad archiveInfo.databaseId: ${archiveInfo.databaseId}"
                }

                resultList += archiveInfo
                thirdPartyArchiveInfoCache.putThirdPartyArchiveInfo(archiveInfo)
            }

            val fetchHistoryMap = localSource.selectLatestFetchHistory(
                    allArchiveDescriptors,
                    DateTime.now().minus(ONE_HOUR),
                    appConstants.archiveFetchHistoryMaxEntries
            )

            fetchHistoryMap.forEach { (archiveDescriptor, fetchHistoryForArchiveList) ->
                val databaseId = fetchHistoryForArchiveList.minBy { fetchHistoryForArchive ->
                    return@minBy fetchHistoryForArchive.databaseId
                }?.databaseId

                if (databaseId != null) {
                    val deletedCount = localSource.deleteOlderThan(archiveDescriptor, databaseId)
                    logger.log(TAG, "deleteOlderThan($archiveDescriptor, $databaseId) -> $deletedCount")
                }

                fetchHistoryForArchiveList.forEach { fetchHistoryResult ->
                    thirdPartyArchiveInfoCache.putThirdPartyArchiveFetchResult(fetchHistoryResult)
                }
            }

            val fetchHistoryDebugInfo = fetchHistoryMap.map { (archiveDescriptor, fetchHistoryForArchiveList) ->
                return@map "$archiveDescriptor: ($fetchHistoryForArchiveList)"
            }.joinToString(separator = ";", prefix = "[", postfix = "]")

            logger.log(TAG, "Loaded ${thirdPartyArchiveInfoList.size} archives, " +
                    "fetchHistoryDebugInfo = $fetchHistoryDebugInfo")

            return@tryWithTransaction resultList
        }

        when (result) {
            is ModularResult.Value -> suspendableInitializer.initWithValue(Unit)
            is ModularResult.Error -> suspendableInitializer.initWithError(result.error)
        }

        if (result is ModularResult.Error) {
            throw result.error
        }

        return (result as ModularResult.Value).value.associateBy { archiveInfo ->
            archiveInfo.archiveDescriptor.domain
        }
    }

    suspend fun insertFetchResult(
            fetchResult: ThirdPartyArchiveFetchResult
    ): ModularResult<ThirdPartyArchiveFetchResult?> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                require(fetchResult.databaseId == 0L) {
                    "Bad fetchResult.databaseId: ${fetchResult.databaseId}"
                }

                val thirdPartyArchiveFetchResult = localSource.insertFetchResult(fetchResult)
                if (thirdPartyArchiveFetchResult != null) {
                    thirdPartyArchiveInfoCache.putThirdPartyArchiveFetchResult(
                            thirdPartyArchiveFetchResult
                    )
                }

                return@tryWithTransaction thirdPartyArchiveFetchResult
            }
        }
    }

    suspend fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult): ModularResult<Unit> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                require(fetchResult.databaseId > 0L) {
                    "Bad fetchResult.databaseId: ${fetchResult.databaseId}"
                }

                localSource.deleteFetchResult(fetchResult)
                thirdPartyArchiveInfoCache.deleteFetchResult(fetchResult)

                return@tryWithTransaction
            }
        }
    }

    suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): ModularResult<Boolean> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                val isEnabled = thirdPartyArchiveInfoCache.isArchiveEnabledOrNull(archiveDescriptor)
                if (isEnabled != null) {
                    return@tryWithTransaction isEnabled!!
                }

                val isActuallyEnabled = localSource.isArchiveEnabled(archiveDescriptor)

                thirdPartyArchiveInfoCache.setArchiveEnabled(archiveDescriptor, isActuallyEnabled)
                return@tryWithTransaction isActuallyEnabled
            }
        }
    }

    suspend fun setArchiveEnabled(
            archiveDescriptor: ArchiveDescriptor,
            isEnabled: Boolean
    ): ModularResult<Unit> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized tryWithTransaction {
                localSource.setArchiveEnabled(archiveDescriptor, isEnabled)
                thirdPartyArchiveInfoCache.setArchiveEnabled(archiveDescriptor, isEnabled)

                return@tryWithTransaction
            }
        }
    }

    /**
     * Returns fetch result history for thread with descriptor [threadDescriptor] for every archive
     * in [archiveDescriptorList] that we had fetched posts from
     * */
    suspend fun selectLatestFetchHistoryForThread(
            archiveDescriptorList: List<ArchiveDescriptor>,
            threadDescriptor: ChanDescriptor.ThreadDescriptor
    ): ModularResult<Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized Try {
                val resultMap = mutableMapOf<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>()
                val newerThan = DateTime.now().minus(ONE_HOUR)

                archiveDescriptorList.forEach { archiveDescriptor ->
                    val threadArchiveFetchHistory = thirdPartyArchiveInfoCache.selectLatestFetchHistoryForThread(
                            archiveDescriptor,
                            threadDescriptor,
                            newerThan,
                            appConstants.archiveFetchHistoryMaxEntries
                    )

                    resultMap[archiveDescriptor] = threadArchiveFetchHistory
                }

                return@Try resultMap.toMap()
            }
        }
    }

    /**
     * Returns the latest N fetch results for this archive
     * */
    suspend fun selectLatestFetchHistory(
            archiveDescriptor: ArchiveDescriptor
    ): ModularResult<List<ThirdPartyArchiveFetchResult>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized Try {
                val newerThan = DateTime.now().minus(ONE_HOUR)

                return@Try thirdPartyArchiveInfoCache.selectLatestFetchHistory(
                        archiveDescriptor,
                        newerThan,
                        appConstants.archiveFetchHistoryMaxEntries
                )
            }
        }
    }

    /**
     * Returns the latest N fetch results for these archives
     * */
    suspend fun selectLatestFetchHistory(
            archiveDescriptorList: List<ArchiveDescriptor>
    ): ModularResult<Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>> {
        return suspendableInitializer.invokeWhenInitialized {
            return@invokeWhenInitialized Try {
                val newerThan = DateTime.now().minus(ONE_HOUR)
                val resultMap = mutableMapOf<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>()

                archiveDescriptorList.forEach { archiveDescriptor ->
                    val fetchHistory = thirdPartyArchiveInfoCache.selectLatestFetchHistory(
                            archiveDescriptor,
                            newerThan,
                            appConstants.archiveFetchHistoryMaxEntries
                    )

                    resultMap[archiveDescriptor] = fetchHistory
                }

                return@Try resultMap.toMap()
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
        return Try {
            return@Try remoteSource.fetchThreadFromNetwork(
                    threadArchiveRequestLink,
                    threadNo,
                    supportsMediaThumbnails,
                    supportsMedia
            )
        }
    }

    companion object {
        // Only select fetch results that were executed no more than 1 hour ago
        private val ONE_HOUR = Duration.standardHours(1)
    }
}