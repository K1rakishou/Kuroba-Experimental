package com.github.adamantcheese.chan.core.manager

import android.graphics.Rect
import androidx.core.view.WindowInsetsCompat
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor

class GlobalWindowInsetsManager {
  private val insetsSubject = PublishProcessor.create<Unit>()
  private val currentInsets = Rect()

  fun updateInsets(insets: WindowInsetsCompat) {
    currentInsets.set(
      insets.systemWindowInsetLeft,
      insets.systemWindowInsetTop,
      insets.systemWindowInsetRight,
      insets.systemWindowInsetBottom
    )

    insetsSubject.onNext(Unit)
  }

  fun listenForInsetsChanges(): Flowable<Unit> {
    return insetsSubject
      .onBackpressureBuffer()
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun left() = currentInsets.left
  fun right() = currentInsets.right
  fun top() = currentInsets.top
  fun bottom() = currentInsets.bottom
}