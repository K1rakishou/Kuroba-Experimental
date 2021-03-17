package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ChanThreadViewableInfo
import com.github.k1rakishou.model.data.thread.ChanThreadViewableInfoView
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository
import com.github.k1rakishou.persist_state.IndexAndTop
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThreadViewableInfoManager(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val chanThreadViewableInfoRepository: ChanThreadViewableInfoRepository
) {
  private val suspendDebouncer = DebouncingCoroutineExecutor(appScope)

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val chanThreadViewableMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanThreadViewableInfo>(128)
  @GuardedBy("lock")
  private val alreadyPreloadedSet = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(128)

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val alreadyPreloaded = lock.read { alreadyPreloadedSet.contains(threadDescriptor) }
    if (alreadyPreloaded) {
      return
    }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) begin")
    }

    val time = measureTime { preloadForThreadInternal(threadDescriptor) }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) end, took $time")
    }
  }

  private suspend fun preloadForThreadInternal(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val chanThreadViewableInfo = chanThreadViewableInfoRepository.preloadForThread(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "preloadForThread($threadDescriptor) failed", error)
        return
      } ?: ChanThreadViewableInfo(threadDescriptor)

    lock.write {
      chanThreadViewableMap[threadDescriptor] = mergeOldAndNewChanThreadViewableInfos(
        chanThreadViewableMap[threadDescriptor],
        chanThreadViewableInfo
      )

      alreadyPreloadedSet.add(threadDescriptor)
    }
  }

  fun getIndexAndTop(threadDescriptor: ChanDescriptor.ThreadDescriptor): IndexAndTop? {
    return lock.read {
      val chanThreadViewableInfo = chanThreadViewableMap[threadDescriptor]
        ?: return@read null

      val listViewIndex = chanThreadViewableInfo.listViewIndex
      val listViewTop = chanThreadViewableInfo.listViewTop

      return@read IndexAndTop(listViewIndex, listViewTop)
    }
  }

  fun getMarkedPostNo(chanDescriptor: ChanDescriptor): Long? {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return null
    }

    val markedPostNo = lock.read { chanThreadViewableMap[chanDescriptor]?.markedPostNo }
      ?: return null

    if (markedPostNo < 0L) {
      return null
    }

    return markedPostNo
  }

  fun getAndConsumeMarkedPostNo(chanDescriptor: ChanDescriptor, func: (Long) -> Unit) {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    val markedPostNo = lock.read { chanThreadViewableMap[chanDescriptor]?.getAndConsumeMarkedPostNo() }
      ?: return

    if (markedPostNo < 0L) {
      return
    }

    func(markedPostNo)
    persist(chanDescriptor)
  }

  fun update(chanDescriptor: ChanDescriptor, mutator: (ChanThreadViewableInfo) -> Unit) {
    update(chanDescriptor, false, mutator)
  }

  // Prefer this method with "createEmptyWhenNull==true" when possible, otherwise the scroll position may
  // not be remembered.
  fun update(chanDescriptor: ChanDescriptor, createEmptyWhenNull: Boolean, mutator: (ChanThreadViewableInfo) -> Unit) {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    var oldChanThreadViewableInfo = lock.read { chanThreadViewableMap[chanDescriptor] }
    if (oldChanThreadViewableInfo == null) {
      if (!createEmptyWhenNull) {
        return
      }

      oldChanThreadViewableInfo = lock.write {
        chanThreadViewableMap[chanDescriptor] = ChanThreadViewableInfo(chanDescriptor)
        chanThreadViewableMap[chanDescriptor]!!
      }
    }

    val mutatedChanThreadViewableInfo = oldChanThreadViewableInfo.deepCopy()
    mutator(mutatedChanThreadViewableInfo)

    if (oldChanThreadViewableInfo == mutatedChanThreadViewableInfo) {
      return
    }

    lock.write { chanThreadViewableMap[chanDescriptor] = mutatedChanThreadViewableInfo }
    persist(chanDescriptor)
  }

  fun <T> view(chanDescriptor: ChanDescriptor, func: (ChanThreadViewableInfoView) -> T): T? {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return null
    }

    val oldChanThreadViewableInfo = lock.read { chanThreadViewableMap[chanDescriptor] }
      ?: return null

    return func(ChanThreadViewableInfoView.fromChanThreadViewableInfo(oldChanThreadViewableInfo))
  }

  private fun mergeOldAndNewChanThreadViewableInfos(
    prev: ChanThreadViewableInfo?,
    current: ChanThreadViewableInfo
  ): ChanThreadViewableInfo {
    if (prev == null) {
      return current
    }

    val listViewIndex = if (prev.listViewIndex > 0 && current.listViewIndex <= 0) {
      prev.listViewIndex
    } else {
      current.listViewIndex
    }

    val listViewTop = if (prev.listViewTop > 0 && current.listViewTop <= 0) {
      prev.listViewTop
    } else {
      current.listViewTop
    }

    return ChanThreadViewableInfo(
      threadDescriptor = current.threadDescriptor,
      listViewIndex = listViewIndex,
      listViewTop = listViewTop,
      lastViewedPostNo = current.lastViewedPostNo,
      lastLoadedPostNo = current.lastLoadedPostNo,
      markedPostNo = prev.markedPostNo,
    )
  }

  private fun persist(chanDescriptor: ChanDescriptor) {
    suspendDebouncer.post(DEBOUNCE_TIME) {
      val chanThreadViewableInfo = lock.read { chanThreadViewableMap[chanDescriptor]?.deepCopy() }
      if (chanThreadViewableInfo != null) {
        chanThreadViewableInfoRepository.persist(chanThreadViewableInfo)
      }
    }
  }

  companion object {
    private const val TAG = "ChanThreadViewableInfoManager"
    private const val DEBOUNCE_TIME = 100L
  }
}