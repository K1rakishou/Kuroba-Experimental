package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ApplicationVisibilityManager {
  private val listeners = CopyOnWriteArrayList<ApplicationVisibilityListener>()

  private var currentApplicationVisibility: ApplicationVisibility = ApplicationVisibility.Background
  private var _switchedToForegroundAt: Long? = null
  val switchedToForegroundAt: Long?
    get() = _switchedToForegroundAt

  fun addListener(listener: ApplicationVisibilityListener) {
    listeners += listener
  }

  fun removeListener(listener: ApplicationVisibilityListener) {
    listeners -= listener
  }

  @OptIn(ExperimentalTime::class)
  fun onEnteredForeground() {
    BackgroundUtils.ensureMainThread()

    _switchedToForegroundAt = System.currentTimeMillis()
    currentApplicationVisibility = ApplicationVisibility.Foreground

    val time = measureTime {
      listeners.forEach { listener ->
        listener.onApplicationVisibilityChanged(currentApplicationVisibility)
      }
    }

    Logger.d(TAG, "onEnteredForeground() callback execution took ${time}, " +
      "callbacks count: ${listeners.size}")
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

    Logger.d(TAG, "onEnteredBackground() callback execution took ${time}, " +
      "callbacks count: ${listeners.size}")
  }

  fun getCurrentAppVisibility(): ApplicationVisibility = currentApplicationVisibility
  fun isAppInForeground(): Boolean = getCurrentAppVisibility() == ApplicationVisibility.Foreground

  // Maybe because the app may get started for whatever reason (service got invoked by the OS) but
  // no activities are going to start up.
  fun isMaybeAppStartingUp(): Boolean = _switchedToForegroundAt == null

  companion object {
    private const val TAG = "ApplicationVisibilityManager"
  }
}

fun interface ApplicationVisibilityListener {
  fun onApplicationVisibilityChanged(applicationVisibility: ApplicationVisibility)
}

sealed class ApplicationVisibility {

  fun isInForeground(): Boolean = this is Foreground
  fun isInBackground(): Boolean = this is Background

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