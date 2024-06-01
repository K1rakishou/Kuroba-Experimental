package com.github.k1rakishou.chan.utils

import androidx.compose.foundation.gestures.GestureCancellationException
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex


private val NoPressGesture: suspend PressGestureScope.(Offset) -> Unit = { }

suspend fun PointerInputScope.detectTapGesturesWithFilter(
  processDownEvent: (Offset) -> Boolean,
  onUpOrCancel: (() -> Unit)? = null,
  onDoubleTap: ((Offset) -> Unit)? = null,
  onLongPress: ((Offset) -> Unit)? = null,
  onPress: suspend PressGestureScope.(Offset) -> Unit = NoPressGesture,
  onTap: ((Offset) -> Unit)? = null
) = coroutineScope {
  // special signal to indicate to the sending side that it shouldn't intercept and consume
  // cancel/up events as we're only require down events
  val pressScope = PressGestureScopeImpl(this@detectTapGesturesWithFilter)

  awaitEachGesture {
    val down = awaitFirstDown()

    if (!processDownEvent(down.position)) {
      return@awaitEachGesture
    }

    if (down.pressed != down.previousPressed) {
      down.consume()
    }

    pressScope.reset()

    if (onPress !== NoPressGesture) {
      launch {
        pressScope.onPress(down.position)
      }
    }

    val longPressTimeout = onLongPress?.let {
      viewConfiguration.longPressTimeoutMillis
    } ?: (Long.MAX_VALUE / 2)

    var upOrCancel: PointerInputChange? = null

    try {
      // wait for first tap up or long press
      upOrCancel = withTimeout(longPressTimeout) {
        waitForUpOrCancellation()
      }
      if (upOrCancel == null) {
        pressScope.cancel() // tap-up was canceled
      } else {
        if (upOrCancel.pressed != upOrCancel.previousPressed) upOrCancel.consume()
        pressScope.release()
      }
    } catch (_: PointerEventTimeoutCancellationException) {
      onLongPress?.invoke(down.position)
      consumeUntilUp()
      pressScope.release()
    }

    if (upOrCancel == null) {
      onUpOrCancel?.invoke()
      return@awaitEachGesture
    }

    // tap was successful.
    if (onDoubleTap == null) {
      onTap?.invoke(upOrCancel.position) // no need to check for double-tap.
      return@awaitEachGesture
    }

    // check for second tap
    val secondDown = awaitSecondDown(upOrCancel)

    if (secondDown == null) {
      onTap?.invoke(upOrCancel.position) // no valid second tap started
    } else {
      // Second tap down detected
      pressScope.reset()
      if (onPress !== NoPressGesture) {
        launch { pressScope.onPress(secondDown.position) }
      }

      try {
        // Might have a long second press as the second tap
        withTimeout(longPressTimeout) {
          val secondUp = waitForUpOrCancellation()
          if (secondUp != null) {
            if (secondUp.pressed != secondUp.previousPressed) secondUp.consume()
            pressScope.release()
            onDoubleTap(secondUp.position)
          } else {
            pressScope.cancel()
            onTap?.invoke(upOrCancel.position)
          }
        }
      } catch (e: PointerEventTimeoutCancellationException) {
        // The first tap was valid, but the second tap is a long press.
        // notify for the first tap
        onTap?.invoke(upOrCancel.position)

        // notify for the long press
        onLongPress?.invoke(secondDown.position)
        consumeUntilUp()
        pressScope.release()
      }
    }
  }
}

private suspend fun AwaitPointerEventScope.awaitSecondDown(
  firstUp: PointerInputChange
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
  val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
  var change: PointerInputChange
  // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
  do {
    change = awaitFirstDown()
  } while (change.uptimeMillis < minUptime)
  change
}

private suspend fun AwaitPointerEventScope.consumeUntilUp() {
  do {
    val event = awaitPointerEvent()
    event.changes.forEach { it.consume() }
  } while (event.changes.any { it.pressed })
}

private class PressGestureScopeImpl(
  density: Density
) : PressGestureScope, Density by density {
  private var isReleased = false
  private var isCanceled = false
  private val mutex = Mutex(locked = false)

  /**
   * Called when a gesture has been canceled.
   */
  fun cancel() {
    isCanceled = true
    mutex.unlock()
  }

  /**
   * Called when all pointers are up.
   */
  fun release() {
    isReleased = true
    mutex.unlock()
  }

  /**
   * Called when a new gesture has started.
   */
  fun reset() {
    mutex.tryLock() // If tryAwaitRelease wasn't called, this will be unlocked.
    isReleased = false
    isCanceled = false
  }

  override suspend fun awaitRelease() {
    if (!tryAwaitRelease()) {
      throw GestureCancellationException("The press gesture was canceled.")
    }
  }

  override suspend fun tryAwaitRelease(): Boolean {
    if (!isReleased && !isCanceled) {
      mutex.lock()
    }
    return isReleased
  }
}