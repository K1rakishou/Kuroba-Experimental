package com.github.adamantcheese.chan.core.manager

import android.graphics.Rect
import androidx.core.view.WindowInsetsCompat
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor

class GlobalWindowInsetsManager {
  private var initialized = false
  var isKeyboardOpened = false
    private set
  private val currentInsets = Rect()

  private val insetsSubject = PublishProcessor.create<Unit>()
  private val keyboardStateSubject = BehaviorProcessor.createDefault(false)

  fun updateIsKeyboardOpened(opened: Boolean) {
    isKeyboardOpened = opened
    keyboardStateSubject.onNext(opened)
  }

  fun updateInsets(insets: WindowInsetsCompat) {
    currentInsets.set(
      insets.systemWindowInsetLeft,
      insets.systemWindowInsetTop,
      insets.systemWindowInsetRight,
      insets.systemWindowInsetBottom
    )

    initialized = true
    insetsSubject.onNext(Unit)
  }

  fun listenForKeyboardChanges(): Flowable<Boolean> {
    return keyboardStateSubject
      .onBackpressureBuffer()
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "keyboardStateSubject unknown error", error) }
      .hide()
  }

  fun listenForInsetsChanges(): Flowable<Unit> {
    return insetsSubject
      .onBackpressureBuffer()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "insetsSubject unknown error", error) }
      .hide()
  }

  fun isInitialized() = initialized
  fun left() = currentInsets.left
  fun right() = currentInsets.right
  fun top() = currentInsets.top
  fun bottom() = currentInsets.bottom

  companion object {
    private const val TAG = "GlobalWindowInsetsManager"
  }
}