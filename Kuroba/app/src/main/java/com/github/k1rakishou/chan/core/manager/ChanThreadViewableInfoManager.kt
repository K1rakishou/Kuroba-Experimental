package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SuspendDebouncer
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ChanThreadViewableInfo
import com.github.k1rakishou.model.data.thread.ChanThreadViewableInfoView
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository
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
  private val suspendDebouncer = SuspendDebouncer(appScope)

  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val chanThreadViewableMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanThreadViewableInfo>(128)

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
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

    return ChanThreadViewableInfo(
      threadDescriptor = current.threadDescriptor,
      listViewIndex = current.listViewIndex,
      listViewTop = current.listViewTop,
      lastViewedPostNo = current.lastViewedPostNo,
      lastLoadedPostNo = current.lastLoadedPostNo,
      markedPostNo = prev.markedPostNo,
    )
  }

  private fun persist(chanDescriptor: ChanDescriptor) {
    // TODO(KurobaEx): we need a debouncer with keys here
    suspendDebouncer.post(DEBOUNCE_TIME) {
      val chanThreadViewableInfo = lock.read { chanThreadViewableMap[chanDescriptor]?.deepCopy() }
      if (chanThreadViewableInfo != null) {
        chanThreadViewableInfoRepository.persist(chanThreadViewableInfo)
      }
    }
  }

  companion object {
    private const val TAG = "ChanThreadViewableInfoManager"
    private const val DEBOUNCE_TIME = 500L
  }
}