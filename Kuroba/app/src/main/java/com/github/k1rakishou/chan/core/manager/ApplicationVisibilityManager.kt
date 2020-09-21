package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ApplicationVisibilityManager {
  private var currentApplicationVisibility: ApplicationVisibility = ApplicationVisibility.Background
  private val listeners = mutableSetOf<ApplicationVisibilityListener>()

  fun addListener(listener: ApplicationVisibilityListener) {
    BackgroundUtils.ensureMainThread()
    listeners += listener
  }

  fun removeListener(listener: ApplicationVisibilityListener) {
    BackgroundUtils.ensureMainThread()
    listeners -= listener
  }

  @OptIn(ExperimentalTime::class)
  fun onEnteredForeground() {
    BackgroundUtils.ensureMainThread()

    currentApplicationVisibility = ApplicationVisibility.Foreground

    val time = measureTime {
      listeners.forEach { listener ->
        listener.onApplicationVisibilityChanged(currentApplicationVisibility)
      }
    }

    Logger.d(TAG, "onEnteredForeground() callback execution took ${time}, callbacks count: ${listeners.size}")
  }

  @OptIn(ExperimentalTime::class)
  fun onEnteredBackground() {
    BackgroundUtils.ensureMainThread()

    currentApplicationVisibility = ApplicationVisibility.Background

    val time = measureTime {
      listeners.forEach { listener ->
        listener.onApplicationVisibilityChanged(currentApplicationVisibility)
      }
    }

    Logger.d(TAG, "onEnteredBackground() callback execution took ${time}, callbacks count: ${listeners.size}")
  }

  fun getCurrentAppVisibility(): ApplicationVisibility = currentApplicationVisibility
  fun isAppInForeground(): Boolean = getCurrentAppVisibility() == ApplicationVisibility.Foreground

  companion object {
    private const val TAG = "ApplicationVisibilityManager"
  }
}

fun interface ApplicationVisibilityListener {
  fun onApplicationVisibilityChanged(applicationVisibility: ApplicationVisibility)
}

sealed class ApplicationVisibility {
  object Foreground : ApplicationVisibility() {
    override fun toString(): String {
      return "Foreground"
    }
  }

  object Background : ApplicationVisibility() {
    override fun toString(): String {
      return "Background"
    }
  }
}