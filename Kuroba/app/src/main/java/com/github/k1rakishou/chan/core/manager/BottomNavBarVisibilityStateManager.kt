package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import java.util.*

class BottomNavBarVisibilityStateManager {
  private val state = BitSet()
  private val replyViewStateSubject = BehaviorProcessor.createDefault(Unit)

  fun listenForViewsStateUpdates(): Flowable<Unit> {
    BackgroundUtils.ensureMainThread()

    return replyViewStateSubject
      .onBackpressureBuffer()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "replyViewStateSubject error", error) }
      .hide()
  }

  fun replyViewStateChanged(isCatalogReplyView: Boolean, isVisible: Boolean) {
    BackgroundUtils.ensureMainThread()

    val bit = if (isCatalogReplyView) {
      CatalogReplyViewBit
    } else {
      ThreadReplyViewBit
    }

    if (isVisible) {
      if (state[bit]) {
        return
      }

      state.set(bit)
    } else {
      if (!state[bit]) {
        return
      }

      state.clear(bit)
    }

    replyViewStateSubject.onNext(Unit)
  }

  fun anyOfViewsIsVisible(): Boolean {
    BackgroundUtils.ensureMainThread()

    return state.nextSetBit(0) >= 0
  }

  fun isThreadReplyLayoutVisible(): Boolean {
    return state.get(ThreadReplyViewBit)
  }

  fun isCatalogReplyLayoutVisible(): Boolean {
    return state.get(CatalogReplyViewBit)
  }

  companion object {
    private const val TAG = "BottomNavBarVisibilityStateManager"

    private const val CatalogReplyViewBit = 1
    private const val ThreadReplyViewBit = 2
  }
}