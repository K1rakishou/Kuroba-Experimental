package com.github.k1rakishou.chan.ui.compose

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCheckbox

@Composable
fun SelectableItem(
  isInSelectionMode: Boolean,
  observeSelectionStateFunc: () -> State<Boolean>,
  onSelectionChanged: (Boolean) -> Unit,
  children: @Composable ColumnScope.() -> Unit
) {
  Row(modifier = Modifier.fillMaxSize()) {
    val transition = updateTransition(
      targetState = isInSelectionMode,
      label = "Selection mode transition"
    )

    val alpha by transition.animateFloat(label = "Selection mode alpha animation") { selection ->
      if (selection) {
        1f
      } else {
        0f
      }
    }

    val width by transition.animateDp(label = "Selection mode checkbox size animation") { selection ->
      if (selection) {
        32.dp
      } else {
        0.dp
      }
    }

    Box(
      modifier = Modifier
        .width(width)
        .alpha(alpha)
    ) {
      if (isInSelectionMode) {
        val checked by observeSelectionStateFunc()

        KurobaComposeCheckbox(
          currentlyChecked = checked,
          onCheckChanged = onSelectionChanged
        )
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      children()
    }
  }
}