package com.github.adamantcheese.chan.core.manager

import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor

class ApplicationVisibilityManager {
  private val appVisibilityStateSubject =
    BehaviorProcessor.createDefault<ApplicationVisibility>(ApplicationVisibility.Background)

  fun listenForAppVisibilityUpdates(): Flowable<ApplicationVisibility> {
    return appVisibilityStateSubject
      .observeOn(AndroidSchedulers.mainThread())
      .distinctUntilChanged()
      .onBackpressureDrop()
      .hide()
  }

  fun onEnteredForeground() {
    appVisibilityStateSubject.onNext(ApplicationVisibility.Foreground)
  }

  fun onEnteredBackground() {
    appVisibilityStateSubject.onNext(ApplicationVisibility.Background)
  }

}

sealed class ApplicationVisibility {
  object Foreground: ApplicationVisibility()
  object Background: ApplicationVisibility()
}