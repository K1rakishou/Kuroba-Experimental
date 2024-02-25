package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R

@Composable
fun KurobaComposeCollapsableContent(
  title: String,
  collapsed: Boolean = true,
  onCollapsedStateChanged: (Boolean) -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .animateContentSize()
  ) {
    Row(
      modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .kurobaClickable(
              bounded = true,
              onClick = { onCollapsedStateChanged(!collapsed) }
          ),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .graphicsLayer { rotationZ = if (collapsed) 0f else 90f },
        drawableId = R.drawable.ic_baseline_arrow_right_24
      )

      Spacer(modifier = Modifier.width(4.dp))

      KurobaComposeText(text = title)

      Spacer(modifier = Modifier.width(4.dp))

      KurobaComposeDivider(
        modifier = Modifier
            .weight(1f)
            .height(1.dp)
      )
    }

    if (!collapsed) {
      Column(
        modifier = Modifier
          .padding(
            horizontal = 4.dp,
            vertical = 2.dp
          )
      ) {
        content()
      }
    }
  }
}