package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.measureTime

class ApplicationVisibilityManager {
  private val listeners = CopyOnWriteArrayList<ApplicationVisibilityListener>()

  private val _applicationVisibilityUpdatesFlow = MutableStateFlow<ApplicationVisibility>(ApplicationVisibility.Background)
  val applicationVisibilityUpdatesFlow: StateFlow<ApplicationVisibility>
    get() = _applicationVisibilityUpdatesFlow.asStateFlow()

  @Volatile
  private var currentApplicationVisibility: ApplicationVisibility = ApplicationVisibility.Background

  @Volatile
  private var _switchedToForegroundAt: Long? = null
  val switchedToForegroundAt: Long?
    get() = _switchedToForegroundAt

  fun addListener(listener: ApplicationVisibilityListener) {
    listeners += listener
  }

  fun removeListener(listener: ApplicationVisibilityListener) {
    listeners -= listener
  }

  fun onEnteredForeground() {
    BackgroundUtils.ensureMainThread()

    _switchedToForegroundAt = System.currentTimeMillis()
    currentApplicationVisibility = ApplicationVisibility.Foreground
    _applicationVisibilityUpdatesFlow.tryEmit(ApplicationVisibility.Foreground)

    val time = measureTime {
      listeners.forEach { listener ->
        listener.onApplicationVisibilityChanged(currentApplicationVisibility)
      }
    }

    Logger.d(TAG, "onEnteredForeground() callback execution took ${time}, " +
      "callbacks count: ${listeners.size}")
  }

  fun onEnteredBackground() {
    BackgroundUtils.ensureMainThread()

    currentApplicationVisibility = ApplicationVisibility.Background
    _applicationVisibilityUpdatesFlow.tryEmit(ApplicationVisibility.Background)

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
  fun isAppInBackground(): Boolean = getCurrentAppVisibility() == ApplicationVisibility.Background

  // Maybe because the app may get started for whatever reason (service got invoked by the OS) but
  // no activities are going to start up.
  fun isAppStartingUpMaybe(): Boolean = _switchedToForegroundAt == null

  suspend fun awaitUntilInForeground() {
    if (getCurrentAppVisibility().isInForeground()) {
      return
    }

    _applicationVisibilityUpdatesFlow.firstOrNull { visibility -> visibility.isInForeground() }
  }

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

  data object Foreground : ApplicationVisibility()
  data object Background : ApplicationVisibility()
}