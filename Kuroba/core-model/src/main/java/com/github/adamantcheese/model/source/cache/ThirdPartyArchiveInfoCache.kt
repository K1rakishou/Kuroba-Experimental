package com.github.adamantcheese.model.source.cache

import androidx.annotation.GuardedBy
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.DateTime

class ThirdPartyArchiveInfoCache {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val thirdPartyArchiveInfoMap = mutableMapOf<ArchiveDescriptor, ThirdPartyArchiveInfo>()

  @GuardedBy("mutex")
  private val thirdPartyArchiveFetchResultMap = mutableMapOf<ArchiveDescriptor, FetchResultsPerThread>()

  @GuardedBy("mutex")
  private val latestFetchResultsMap = mutableMapOf<ArchiveDescriptor, MutableList<ThirdPartyArchiveFetchResult>>()

  suspend fun putThirdPartyArchiveInfo(thirdPartyArchiveInfo: ThirdPartyArchiveInfo) {
    mutex.withLock {
      thirdPartyArchiveInfoMap.put(thirdPartyArchiveInfo.archiveDescriptor, thirdPartyArchiveInfo)
    }
  }

  suspend fun putThirdPartyArchiveFetchResult(fetchResult: ThirdPartyArchiveFetchResult) {
    mutex.withLock {
      val archiveDescriptor = fetchResult.archiveDescriptor

      if (!thirdPartyArchiveFetchResultMap.containsKey(archiveDescriptor)) {
        thirdPartyArchiveFetchResultMap[archiveDescriptor] = FetchResultsPerThread()
      }

      if (!latestFetchResultsMap.containsKey(fetchResult.archiveDescriptor)) {
        latestFetchResultsMap[fetchResult.archiveDescriptor] = mutableListOf()
      }

      latestFetchResultsMap[fetchResult.archiveDescriptor]!!.add(fetchResult)
      thirdPartyArchiveFetchResultMap[archiveDescriptor]!!.addFetchResult(fetchResult)
    }
  }

  suspend fun selectLatestFetchHistoryForThread(
    archiveDescriptor: ArchiveDescriptor,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    newerThan: DateTime,
    archiveFetchHistoryMaxEntries: Int
  ): List<ThirdPartyArchiveFetchResult> {
    return mutex.withLock {
      if (!thirdPartyArchiveFetchResultMap.containsKey(archiveDescriptor)) {
        return@withLock emptyList()
      }

      return thirdPartyArchiveFetchResultMap[archiveDescriptor]!!.selectLatestFetchHistory(
        threadDescriptor,
        newerThan,
        archiveFetchHistoryMaxEntries
      )
    }
  }

  suspend fun selectLatestFetchHistory(
    archiveDescriptor: ArchiveDescriptor,
    newerThan: DateTime,
    archiveFetchHistoryMaxEntries: Int
  ): List<ThirdPartyArchiveFetchResult> {
    return mutex.withLock {
      if (!latestFetchResultsMap.containsKey(archiveDescriptor)) {
        return@withLock emptyList()
      }

      val sortedFetchResults = latestFetchResultsMap[archiveDescriptor]
        ?.sortedByDescending { fetchResult -> fetchResult.insertedOn }
        ?: return emptyList()

      val toReturnList = mutableListOf<ThirdPartyArchiveFetchResult>()
      val toRemoveList = mutableListOf<ThirdPartyArchiveFetchResult>()

      sortedFetchResults.forEach { thirdPartyArchiveFetchResult ->
        if (thirdPartyArchiveFetchResult.insertedOn > newerThan
          && toReturnList.size < archiveFetchHistoryMaxEntries) {
          toReturnList.add(thirdPartyArchiveFetchResult)
        } else {
          toRemoveList.add(thirdPartyArchiveFetchResult)
        }
      }

      if (toRemoveList.isNotEmpty()) {
        toRemoveList.forEach { toRemove ->
          latestFetchResultsMap[archiveDescriptor]!!.remove(toRemove)
        }
      }

      return@withLock toReturnList
    }
  }

  suspend fun isArchiveEnabledOrNull(archiveDescriptor: ArchiveDescriptor): Boolean? {
    return mutex.withLock {
      return@withLock thirdPartyArchiveInfoMap[archiveDescriptor]?.enabled
    }
  }

  suspend fun setArchiveEnabled(archiveDescriptor: ArchiveDescriptor, isEnabled: Boolean) {
    mutex.withLock {
      val updatedArchiveInfo = thirdPartyArchiveInfoMap[archiveDescriptor]!!
        .copy(enabled = isEnabled)

      thirdPartyArchiveInfoMap[archiveDescriptor] = updatedArchiveInfo
    }
  }

  suspend fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult) {
    mutex.withLock {
      val archiveDescriptor = fetchResult.archiveDescriptor

      if (!thirdPartyArchiveFetchResultMap.containsKey(archiveDescriptor)) {
        return@withLock
      }

      latestFetchResultsMap[fetchResult.archiveDescriptor]?.remove(fetchResult)
      thirdPartyArchiveFetchResultMap[archiveDescriptor]!!.deleteFetchResult(fetchResult)
    }
  }

  suspend fun deleteOld(olderThan: DateTime) {
    mutex.withLock {
      thirdPartyArchiveFetchResultMap.values.forEach { fetchResultsPerThread ->
        fetchResultsPerThread.deleteOld(olderThan)
      }
    }
  }

}

internal class FetchResultsPerThread {
  private val fetchResultsMap: MutableMap<ChanDescriptor.ThreadDescriptor, MutableList<ThirdPartyArchiveFetchResult>> = mutableMapWithCap(32)

  internal fun addFetchResult(fetchResult: ThirdPartyArchiveFetchResult) {
    if (!fetchResultsMap.containsKey(fetchResult.threadDescriptor)) {
      fetchResultsMap[fetchResult.threadDescriptor] = mutableListOf()
    }

    fetchResultsMap[fetchResult.threadDescriptor]!!.add(fetchResult)
  }

  fun selectLatestFetchHistory(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    newerThan: DateTime,
    archiveFetchHistoryMaxEntries: Int
  ): List<ThirdPartyArchiveFetchResult> {
    if (!fetchResultsMap.containsKey(threadDescriptor)) {
      return emptyList()
    }

    val sortedFetchResults = fetchResultsMap[threadDescriptor]
      ?.sortedByDescending { fetchResult -> fetchResult.insertedOn }
      ?: return emptyList()

    val toReturnList = mutableListOf<ThirdPartyArchiveFetchResult>()
    val toRemoveList = mutableListOf<ThirdPartyArchiveFetchResult>()

    sortedFetchResults.forEach { thirdPartyArchiveFetchResult ->
      if (thirdPartyArchiveFetchResult.insertedOn > newerThan
        && toReturnList.size < archiveFetchHistoryMaxEntries) {
        toReturnList.add(thirdPartyArchiveFetchResult)
      } else {
        toRemoveList.add(thirdPartyArchiveFetchResult)
      }
    }

    if (toRemoveList.isNotEmpty()) {
      toRemoveList.forEach { toRemove ->
        fetchResultsMap[threadDescriptor]!!.remove(toRemove)
      }
    }

    return toReturnList
  }

  internal fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult) {
    fetchResultsMap[fetchResult.threadDescriptor]?.remove(fetchResult)
  }

  internal fun deleteOld(olderThan: DateTime) {
    fetchResultsMap.values.forEach { fetchResultList ->
      val iter = fetchResultList.iterator()

      while (iter.hasNext()) {
        val thirdPartyArchiveFetchResult = iter.next()
        if (thirdPartyArchiveFetchResult.insertedOn < olderThan) {
          iter.remove()
        }
      }
    }
  }

}