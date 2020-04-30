package com.github.adamantcheese.model.source.cache

import androidx.annotation.GuardedBy
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.DateTime

class ThirdPartyArchiveInfoCache {
    private val mutex = Mutex()

    @GuardedBy("mutex")
    private val thirdPartyArchiveInfoMap =
            mutableMapOf<ArchiveDescriptor, ThirdPartyArchiveInfo>()
    @GuardedBy("mutex")
    private val thirdPartyArchiveFetchResultMap =
            mutableMapOf<ArchiveDescriptor, MutableList<ThirdPartyArchiveFetchResult>>()

    suspend fun putThirdPartyArchiveInfo(thirdPartyArchiveInfo: ThirdPartyArchiveInfo) {
        mutex.withLock {
            thirdPartyArchiveInfoMap.put(thirdPartyArchiveInfo.archiveDescriptor, thirdPartyArchiveInfo)
        }
    }

    suspend fun putThirdPartyArchiveFetchResult(thirdPartyArchiveFetchResult: ThirdPartyArchiveFetchResult) {
        mutex.withLock {
            val archiveDescriptor = thirdPartyArchiveFetchResult.archiveDescriptor

            if (!thirdPartyArchiveFetchResultMap.containsKey(archiveDescriptor)) {
                thirdPartyArchiveFetchResultMap[archiveDescriptor] = mutableListOf()
            }

            thirdPartyArchiveFetchResultMap[archiveDescriptor]!!.add(thirdPartyArchiveFetchResult)
        }
    }

    suspend fun selectLatestFetchHistory(
            archiveDescriptor: ArchiveDescriptor,
            newerThan: DateTime,
            archiveFetchHistoryMaxEntries: Int
    ): List<ThirdPartyArchiveFetchResult> {
        return mutex.withLock {
            if (!thirdPartyArchiveInfoMap.containsKey(archiveDescriptor)) {
                return@withLock emptyList()
            }

            val fetchResults = thirdPartyArchiveFetchResultMap[archiveDescriptor]
                    ?: return@withLock emptyList()

            val sortedFetchResults = fetchResults.sortedByDescending { fetchResult ->
                fetchResult.insertedOn
            }

            val toReturnList = mutableListOf<ThirdPartyArchiveFetchResult>()
            val toRemoveList = mutableListOf<ThirdPartyArchiveFetchResult>()

            sortedFetchResults.forEach { thirdPartyArchiveFetchResult ->
                if (thirdPartyArchiveFetchResult.insertedOn > newerThan
                        && toReturnList.size <= archiveFetchHistoryMaxEntries) {
                    toReturnList.add(thirdPartyArchiveFetchResult)
                } else {
                    toRemoveList.add(thirdPartyArchiveFetchResult)
                }
            }

            if (toRemoveList.isNotEmpty()) {
                toRemoveList.forEach { toRemove ->
                    thirdPartyArchiveFetchResultMap[archiveDescriptor]!!.remove(toRemove)
                }
            }

            return@withLock toReturnList
        }
    }

    suspend fun isArchiveEnabled(archiveDescriptor: ArchiveDescriptor): Boolean {
        return mutex.withLock {
            return@withLock thirdPartyArchiveInfoMap[archiveDescriptor]!!.enabled
        }
    }

    suspend fun setArchiveEnabled(archiveDescriptor: ArchiveDescriptor, isEnabled: Boolean) {
        mutex.withLock {
            val updatedArchiveInfo = thirdPartyArchiveInfoMap[archiveDescriptor]!!
                    .copy(enabled = isEnabled)

            thirdPartyArchiveInfoMap[archiveDescriptor] = updatedArchiveInfo
        }
    }

    suspend fun deleteFetchResult(thirdPartyArchiveFetchResult: ThirdPartyArchiveFetchResult) {
        mutex.withLock {
            val archiveDescriptor = thirdPartyArchiveFetchResult.archiveDescriptor

            if (!thirdPartyArchiveFetchResultMap.containsKey(archiveDescriptor)) {
                return@withLock
            }

            thirdPartyArchiveFetchResultMap[archiveDescriptor]!!.remove(thirdPartyArchiveFetchResult)
        }
    }

    suspend fun deleteOld(olderThan: DateTime) {
        mutex.withLock {
            thirdPartyArchiveFetchResultMap.values.forEach { thirdPartyArchiveFetchResultList ->
                val iter = thirdPartyArchiveFetchResultList.iterator()

                while (iter.hasNext()) {
                    val thirdPartyArchiveFetchResult = iter.next()
                    if (thirdPartyArchiveFetchResult.insertedOn < olderThan) {
                        iter.remove()
                    }
                }
            }
        }
    }

}