package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfo
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfoView
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThreadViewableInfoManager {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val chanThreadViewableMap =
    mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanThreadViewableInfo>(128)

  private val suspendableInitializer = SuspendableInitializer<Unit>("ChanThreadViewableInfoManager")


  fun init() {
    // TODO(KurobaEx):
    suspendableInitializer.initWithValue(Unit)
  }

  fun getAndConsumeMarkedPostNo(chanDescriptor: ChanDescriptor, func: (Long) -> Unit) {
    check(isReady()) { "ChanThreadViewableInfoManager is not ready yet! Use awaitUntilInitialized()" }

    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    val markedPostNo = lock.read { chanThreadViewableMap[chanDescriptor]?.getAndConsumeMarkedPostNo() }
      ?: return

    func(markedPostNo)

    // TODO(KurobaEx): persist
  }

  fun update(chanDescriptor: ChanDescriptor, mutator: (ChanThreadViewableInfo) -> Unit) {
    check(isReady()) { "ChanThreadViewableInfoManager is not ready yet! Use awaitUntilInitialized()" }

    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    val oldChanThreadViewableInfo = chanThreadViewableMap[chanDescriptor]
      ?: return

    val mutatedChanThreadViewableInfo = oldChanThreadViewableInfo.deepCopy()
    mutator(mutatedChanThreadViewableInfo)

    if (oldChanThreadViewableInfo == mutatedChanThreadViewableInfo) {
      return
    }

    chanThreadViewableMap[chanDescriptor] = mutatedChanThreadViewableInfo

    // TODO(KurobaEx): persist
  }

  fun <T> view(chanDescriptor: ChanDescriptor, func: (ChanThreadViewableInfoView) -> T): T? {
    check(isReady()) { "ChanThreadViewableInfoManager is not ready yet! Use awaitUntilInitialized()" }

    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return null
    }

    val oldChanThreadViewableInfo = chanThreadViewableMap[chanDescriptor]
      ?: return null

    return func(ChanThreadViewableInfoView.fromChanThreadViewableInfo(oldChanThreadViewableInfo))
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "ChanThreadViewableInfoManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "ChanThreadViewableInfoManager initialization completed, took $duration")
  }

  fun isReady() = suspendableInitializer.isInitialized()

  companion object {
    private const val TAG = "ChanThreadViewableInfoManager"
  }

}