package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.utils.BackgroundUtils
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import java.util.*

class ReplyViewStateManager {
  private val state = BitSet()
  private val replyViewStateSubject = BehaviorProcessor.createDefault(Unit)

  fun listenForReplyViewsStateUpdates(): Flowable<Unit> {
    BackgroundUtils.ensureMainThread()

    return replyViewStateSubject
      .observeOn(AndroidSchedulers.mainThread())
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
      state.set(bit)
    } else {
      state.clear(bit)
    }

    replyViewStateSubject.onNext(Unit)
  }

  fun anyReplyViewVisible(): Boolean {
    BackgroundUtils.ensureMainThread()

    return state.nextSetBit(0) >= 0
  }

  companion object {
    private const val CatalogReplyViewBit = 1 shl 0
    private const val ThreadReplyViewBit = 1 shl 1
  }
}