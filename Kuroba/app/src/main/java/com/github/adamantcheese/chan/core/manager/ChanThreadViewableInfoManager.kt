package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.base.SuspendDebouncer
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfo
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfoView
import com.github.adamantcheese.model.repository.ChanThreadViewableInfoRepository
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ChanThreadViewableInfoManager(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val chanThreadViewableInfoRepository: ChanThreadViewableInfoRepository
) {
  private val suspendDebouncer = SuspendDebouncer(appScope)

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val chanThreadViewableMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanThreadViewableInfo>(128)

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) begin")
    }

    val chanThreadViewableInfo = chanThreadViewableInfoRepository.preloadForThread(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "preloadForThread($threadDescriptor) failed", error)
        return
      } ?: ChanThreadViewableInfo(threadDescriptor)

    lock.write { chanThreadViewableMap[threadDescriptor] = chanThreadViewableInfo }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) end")
    }
  }

  fun getAndConsumeMarkedPostNo(chanDescriptor: ChanDescriptor, func: (Long) -> Unit) {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    val markedPostNo = lock.read { chanThreadViewableMap[chanDescriptor]?.getAndConsumeMarkedPostNo() }
      ?: return

    func(markedPostNo)
    persist(chanDescriptor)
  }

  fun update(chanDescriptor: ChanDescriptor, mutator: (ChanThreadViewableInfo) -> Unit) {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    val oldChanThreadViewableInfo = lock.read { chanThreadViewableMap[chanDescriptor] }
      ?: return

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