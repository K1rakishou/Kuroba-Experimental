package com.github.k1rakishou.chan.ui.compose.badge

import android.content.res.Configuration
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.helper.PinHelper
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.reactive.asFlow

sealed class MenuItemBadge {
  data object Dot : MenuItemBadge()

  data class Counter(
    val counter: Int,
    val highlight: Boolean = false
  ) : MenuItemBadge()
}

@Composable
fun BoxScope.BottomPanelBadge(menuItemBadge: MenuItemBadge) {
  val chanTheme = LocalChanTheme.current
  val configuration = LocalConfiguration.current

  val themeEngine = appDependencies().themeEngine

  when (menuItemBadge) {
    MenuItemBadge.Dot -> {
      Box(
        modifier = Modifier
          .size(10.dp)
          .offset(x = 8.dp, y = 8.dp)
          .background(color = chanTheme.accentColorCompose, shape = CircleShape)
          .align(Alignment.TopCenter)
      )
    }
    is MenuItemBadge.Counter -> {
      val counter by animateIntAsState(
        targetValue = menuItemBadge.counter,
        label = "Menu item badge counter animation"
      )
      val highlight = menuItemBadge.highlight

      val watchEnabled by ChanSettings.watchEnabled.listenForChangesDeprecated()
        .asFlow()
        .collectAsState(initial = false)

      val backgroundColor = if (!watchEnabled) {
        chanTheme.bookmarkCounterNotWatchingColorCompose
      } else if (highlight) {
        chanTheme.bookmarkCounterHasRepliesColorCompose
      } else {
        chanTheme.bookmarkCounterNormalColorCompose
      }

      val textColor = remember(key1 = backgroundColor) {
        if (ThemeEngine.isDarkColor(backgroundColor)) {
          Color.White
        } else {
          Color.Black
        }
      }

      val counterText = remember(key1 = counter) { PinHelper.getShortUnreadCount(counter) }

      val fontSize = when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> 10.ktu.fixedSize()
        Configuration.ORIENTATION_LANDSCAPE -> 11.ktu.fixedSize()
        else -> 10.ktu.fixedSize()
      }

      val offsetY = when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT ->  (-4).dp
        Configuration.ORIENTATION_LANDSCAPE -> (-8).dp
        else ->  (-4).dp
      }

      KurobaComposeText(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .offset(x = 0.dp, y = offsetY)
          .drawWithContent {
            drawRoundRect(
              color = backgroundColor,
              alpha = 0.85f,
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            drawContent()
          }
          .padding(horizontal = 4.dp),
        text = counterText,
        color = textColor,
        fontSize = fontSize
      )
    }
  }
}