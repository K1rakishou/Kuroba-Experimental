package com.github.k1rakishou.chan.ui.compose.reorder

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

/**
 * Taken from https://github.com/aclassen/ComposeReorderable
 * */


fun Modifier.draggedItem(
  offset: Float?,
  orientation: Orientation = Orientation.Vertical,
): Modifier = composed {
  Modifier
    .zIndex(offset?.let { 1f } ?: 0f)
    .graphicsLayer {
      with(offset ?: 0f) {
        if (orientation == Orientation.Vertical) {
          translationY = this
        } else {
          translationX = this
        }
      }
      shadowElevation = offset?.let { 8f } ?: 0f
    }
}