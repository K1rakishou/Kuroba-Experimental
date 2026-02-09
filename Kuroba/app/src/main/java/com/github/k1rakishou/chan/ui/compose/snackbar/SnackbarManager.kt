package com.github.k1rakishou.chan.ui.compose.snackbar

import android.widget.Toast
import androidx.annotation.StringRes
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.ScopedSnackbarManager
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration

interface SnackbarManager {
  val snackbarEventFlow: SharedFlow<SnackbarInfoEvent>
  val swipedAwaySnackbars: SharedFlow<SnackbarInfo>

  fun onControllerCreated(controllerKey: ControllerKey)
  fun onControllerDestroyed(controllerKey: ControllerKey)
  fun onSnackbarSwipedAway(snackbarInfo: SnackbarInfo)

  fun snackbar(
    snackbarId: SnackbarId,
    lifetime: Duration? = ScopedSnackbarManager.STANDARD_DURATION,
    text: SnackbarContentItem.Text,
    button: SnackbarContentItem.Button? = null
  ): SnackbarId

  fun toast(
    @StringRes messageId: Int,
    toastId: String = ScopedSnackbarManager.nextToastId(),
    duration: Duration = ScopedSnackbarManager.STANDARD_DURATION
  ): SnackbarId

  fun toast(
    message: String,
    toastId: String = ScopedSnackbarManager.nextToastId(),
    duration: Duration = ScopedSnackbarManager.STANDARD_DURATION
  ): SnackbarId

  fun errorToast(
    @StringRes messageId: Int,
    toastId: String = ScopedSnackbarManager.nextToastId(),
    duration: Duration = ScopedSnackbarManager.LONG_DURATION
  ): SnackbarId

  fun errorToast(
    message: String,
    toastId: String = ScopedSnackbarManager.nextToastId(),
    duration: Duration = ScopedSnackbarManager.LONG_DURATION
  ): SnackbarId

  fun hideToast(
    toastId: String
  )

  fun globalToast(
    message: String,
    toastDuration: Int = Toast.LENGTH_SHORT
  )

  fun globalErrorToast(
    message: String,
    toastDuration: Int = Toast.LENGTH_LONG
  )

  fun showSnackbar(snackbarInfo: SnackbarInfo)
  fun dismissSnackbar(snackbarId: SnackbarId)

  fun onSnackbarCreated(snackbarId: SnackbarId, snackbarScope: SnackbarScope)
  fun onSnackbarDestroyed(snackbarId: SnackbarId, snackbarScope: SnackbarScope)

  class RemovedSnackbarInfo(
    val snackbarId: SnackbarId,
    val timedOut: Boolean
  )
}