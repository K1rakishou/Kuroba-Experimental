package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor

class ApplicationVisibilityManager {
  private val appVisibilityStateSubject =
    BehaviorProcessor.createDefault<ApplicationVisibility>(ApplicationVisibility.Background)

  fun listenForAppVisibilityUpdates(): Flowable<ApplicationVisibility> {
    return appVisibilityStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "listenForAppVisibilityUpdates error", error) }
      .distinctUntilChanged()
      .hide()
  }

  fun onEnteredForeground() {
    appVisibilityStateSubject.onNext(ApplicationVisibility.Foreground)
  }

  fun onEnteredBackground() {
    appVisibilityStateSubject.onNext(ApplicationVisibility.Background)
  }

  fun getCurrentAppVisibility(): ApplicationVisibility = appVisibilityStateSubject.value
    ?: ApplicationVisibility.Background

  fun isAppInForeground(): Boolean = getCurrentAppVisibility() == ApplicationVisibility.Foreground

  companion object {
    private const val TAG = "ApplicationVisibilityManager"
  }
}

sealed class ApplicationVisibility {
  object Foreground: ApplicationVisibility() {
    override fun toString(): String {
      return "Foreground"
    }
  }

  object Background: ApplicationVisibility() {
    override fun toString(): String {
      return "Background"
    }
  }
}