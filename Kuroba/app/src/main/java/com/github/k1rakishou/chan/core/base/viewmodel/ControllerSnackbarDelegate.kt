package com.github.k1rakishou.chan.core.base.viewmodel

import com.github.k1rakishou.chan.ui.compose.snackbar.manager.ScopedSnackbarManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration

interface ControllerSnackbarDelegate {
  val snackbarManagerEvents: SharedFlow<Toast>

  fun toast(
    message: String,
    toastId: String = ScopedSnackbarManager.nextToastId(),
    duration: Duration = ScopedSnackbarManager.STANDARD_DURATION
  )

  fun errorToast(
    message: String,
    toastId: String = ScopedSnackbarManager.nextToastId(),
    duration: Duration = ScopedSnackbarManager.STANDARD_DURATION
  )

  data class Toast(
    val error: Boolean,
    val message: String,
    val toastId: String,
    val duration: Duration
  )
}

class HasSnackbarDelegateImpl : ControllerSnackbarDelegate {
  override val snackbarManagerEvents: MutableSharedFlow<ControllerSnackbarDelegate.Toast> =
    MutableSharedFlow<ControllerSnackbarDelegate.Toast>(extraBufferCapacity = Channel.UNLIMITED)

  override fun toast(message: String, toastId: String, duration: Duration) {
    emitToastEvent(false, message, toastId, duration)
  }

  override fun errorToast(message: String, toastId: String, duration: Duration) {
    emitToastEvent(true, message, toastId, duration)
  }

  private fun emitToastEvent(error: Boolean, message: String, toastId: String, duration: Duration) {
    val toast = ControllerSnackbarDelegate.Toast(
      error = error,
      message = message,
      toastId = toastId,
      duration = duration
    )

    snackbarManagerEvents.tryEmit(toast)
  }
}