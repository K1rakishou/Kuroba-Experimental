package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.base.SerializedCoroutineExecutor
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfo
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfoView
import com.github.adamantcheese.model.repository.ChanThreadViewableInfoRepository
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

class ChanThreadViewableInfoManager(
  private val chanThreadViewableInfoRepository: ChanThreadViewableInfoRepository,
  private val appScope: CoroutineScope
) {
  private val lock = ReentrantReadWriteLock()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  @GuardedBy("lock")
  private val chanThreadViewableMap =
    mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanThreadViewableInfo>(128)

  suspend fun preloadForThread(chanDescriptor: ChanDescriptor) {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    val chanThreadViewableInfo = chanThreadViewableInfoRepository.preloadForThread(chanDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "preloadForThread($chanDescriptor) failed", error)
        return
      } ?: ChanThreadViewableInfo(chanDescriptor)

    chanThreadViewableMap[chanDescriptor] = chanThreadViewableInfo
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

    val oldChanThreadViewableInfo = chanThreadViewableMap[chanDescriptor]
      ?: return

    val mutatedChanThreadViewableInfo = oldChanThreadViewableInfo.deepCopy()
    mutator(mutatedChanThreadViewableInfo)

    if (oldChanThreadViewableInfo == mutatedChanThreadViewableInfo) {
      return
    }

    chanThreadViewableMap[chanDescriptor] = mutatedChanThreadViewableInfo
    persist(chanDescriptor)
  }

  fun <T> view(chanDescriptor: ChanDescriptor, func: (ChanThreadViewableInfoView) -> T): T? {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return null
    }

    val oldChanThreadViewableInfo = chanThreadViewableMap[chanDescriptor]
      ?: return null

    return func(ChanThreadViewableInfoView.fromChanThreadViewableInfo(oldChanThreadViewableInfo))
  }

  private fun persist(chanDescriptor: ChanDescriptor) {
    serializedCoroutineExecutor.post {
      chanThreadViewableMap[chanDescriptor]?.let { chanThreadViewableInfo ->
        chanThreadViewableInfoRepository.persist(chanThreadViewableInfo)
      }
    }
  }

  companion object {
    private const val TAG = "ChanThreadViewableInfoManager"
  }
}