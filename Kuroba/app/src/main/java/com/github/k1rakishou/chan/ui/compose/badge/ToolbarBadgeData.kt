package com.github.k1rakishou.chan.ui.compose.badge

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.helper.PinHelper
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

data class ToolbarBadgeData(
  val counter: Int,
  val highlight: Boolean
)

@Composable
fun BoxScope.ToolbarBadge(
  chanTheme: ChanTheme,
  toolbarBadge: ToolbarBadgeData
) {
  val kurobaSettings = appDependencies().kurobaSettings

  val counter by animateIntAsState(
    targetValue = toolbarBadge.counter,
    label = "Menu item badge counter animation"
  )
  val highlight = toolbarBadge.highlight
  val watchEnabled by kurobaSettings.application.watchEnabled.listen().collectAsState(initial = true)

  val backgroundColor = if (!watchEnabled) {
    chanTheme.bookmarkCounterNotWatchingColorCompose
  } else if (highlight) {
    chanTheme.bookmarkCounterHasRepliesColorCompose
  } else {
    chanTheme.bookmarkCounterNormalColorCompose
  }

  val textColor = if (ThemeEngine.isDarkColor(backgroundColor)) {
    Color.White
  } else {
    Color.Black
  }

  if (counter > 0) {
    val counterText = remember(key1 = counter) { PinHelper.getShortUnreadCount(counter) }

    KurobaComposeText(
      modifier = Modifier
        .align(Alignment.Center)
        .offset(x = 10.dp, y = (-10).dp)
        .drawWithContent {
          val iconPaddingPx = 2.dp.toPx()
          val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

          drawRoundRect(
            color = chanTheme.toolbarBackgroundComposeColor,
            topLeft = Offset.Zero - Offset(iconPaddingPx, iconPaddingPx),
            size = Size(
              width = this.size.width + (iconPaddingPx * 2),
              height = this.size.height + (iconPaddingPx * 2)
            ),
            cornerRadius = cornerRadius
          )

          drawRoundRect(
            color = backgroundColor,
            alpha = 0.85f,
            cornerRadius = cornerRadius
          )

          drawContent()
        }
        .padding(horizontal = 2.dp),
      text = counterText,
      color = textColor,
      fontSize = 10.ktu.fixedSize()
    )
  }
}