package com.github.k1rakishou.chan.ui.compose.reorder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeConsumed
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout


/**
 * Taken from https://github.com/aclassen/ComposeReorderable
 * */


// Copied from DragGestureDetector , as long the pointer api isn`t ready.

internal suspend fun AwaitPointerEventScope.awaitPointerSlopOrCancellation(
  pointerId: PointerId,
  pointerType: PointerType,
  onPointerSlopReached: (change: PointerInputChange, overSlop: Offset) -> Unit,
): PointerInputChange? {
  if (currentEvent.isPointerUp(pointerId)) {
    return null // The pointer has already been lifted, so the gesture is canceled
  }
  var offset = Offset.Zero
  val touchSlop = viewConfiguration.pointerSlop(pointerType)
  var pointer = pointerId
  while (true) {
    val event = awaitPointerEvent()
    val dragEvent = event.changes.firstOrNull { it.id == pointer }!!
    if (dragEvent.positionChangeConsumed()) {
      return null
    } else if (dragEvent.changedToUpIgnoreConsumed()) {
      val otherDown = event.changes.firstOrNull { it.pressed }
      if (otherDown == null) {
        // This is the last "up"
        return null
      } else {
        pointer = otherDown.id
      }
    } else {
      offset += dragEvent.positionChange()
      val distance = offset.getDistance()
      var acceptedDrag = false
      if (distance >= touchSlop) {
        val touchSlopOffset = offset / distance * touchSlop
        onPointerSlopReached(dragEvent, offset - touchSlopOffset)
        if (dragEvent.positionChangeConsumed()) {
          acceptedDrag = true
        } else {
          offset = Offset.Zero
        }
      }

      if (acceptedDrag) {
        return dragEvent
      } else {
        awaitPointerEvent(PointerEventPass.Final)
        if (dragEvent.positionChangeConsumed()) {
          return null
        }
      }
    }
  }
}

internal suspend fun PointerInputScope.awaitLongPressOrCancellation(
  initialDown: PointerInputChange,
): PointerInputChange? {
  var longPress: PointerInputChange? = null
  var currentDown = initialDown
  val longPressTimeout = viewConfiguration.longPressTimeoutMillis
  return try {
    // wait for first tap up or long press
    withTimeout(longPressTimeout) {
      awaitPointerEventScope {
        var finished = false
        while (!finished) {
          val event = awaitPointerEvent(PointerEventPass.Main)
          if (event.changes.all { it.changedToUpIgnoreConsumed() }) {
            // All pointers are up
            finished = true
          }

          if (
            event.changes.any { it.consumed.downChange || it.isOutOfBounds(size) }
          ) {
            finished = true // Canceled
          }

          // Check for cancel by position consumption. We can look on the Final pass of
          // the existing pointer event because it comes after the Main pass we checked
          // above.
          val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
          if (consumeCheck.changes.any { it.positionChangeConsumed() }) {
            finished = true
          }
          if (!event.isPointerUp(currentDown.id)) {
            longPress = event.changes.firstOrNull { it.id == currentDown.id }
          } else {
            val newPressed = event.changes.firstOrNull { it.pressed }
            if (newPressed != null) {
              currentDown = newPressed
              longPress = currentDown
            } else {
              // should technically never happen as we checked it above
              finished = true
            }
          }
        }
      }
    }
    null
  } catch (_: TimeoutCancellationException) {
    longPress ?: initialDown
  }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
  changes.firstOrNull { it.id == pointerId }?.pressed != true

// This value was determined using experiments and common sense.
// We can't use zero slop, because some hypothetical desktop/mobile devices can send
// pointer events with a very high precision (but I haven't encountered any that send
// events with less than 1px precision)
private val mouseSlop = 0.125.dp
private val defaultTouchSlop = 18.dp // The default touch slop on Android devices
private val mouseToTouchSlopRatio = mouseSlop / defaultTouchSlop


private fun ViewConfiguration.pointerSlop(pointerType: PointerType): Float {
  return when (pointerType) {
    PointerType.Mouse -> touchSlop * mouseToTouchSlopRatio
    else -> touchSlop
  }
}