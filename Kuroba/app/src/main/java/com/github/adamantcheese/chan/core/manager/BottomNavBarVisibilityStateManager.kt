package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
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

  fun searchViewStateChanged(isVisible: Boolean) {
    BackgroundUtils.ensureMainThread()

    if (isVisible) {
      if (state[SearchViewBut]) {
        return
      }

      state.set(SearchViewBut)
    } else {
      if (!state[SearchViewBut]) {
        return
      }

      state.clear(SearchViewBut)
    }

    replyViewStateSubject.onNext(Unit)
  }

  fun anyViewIsVisible(): Boolean {
    BackgroundUtils.ensureMainThread()

    return state.nextSetBit(0) >= 0
  }

  companion object {
    private const val TAG = "BottomNavBarVisibilityStateManager"

    private const val CatalogReplyViewBit = 1 shl 0
    private const val ThreadReplyViewBit = 1 shl 1
    private const val SearchViewBut = 1 shl 2
  }
}