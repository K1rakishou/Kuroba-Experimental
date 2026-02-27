package com.github.k1rakishou.chan.features.toolbar.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.clearText
import com.github.k1rakishou.chan.ui.compose.collectText
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeClickableIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.resolveIconTintColor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun ToolbarTitleWithSubtitle(
  modifier: Modifier,
  title: ToolbarText,
  subtitle: ToolbarText?,
  chanTheme: ChanTheme,
  scrollableTitle: Boolean
) {
  val textColor = chanTheme.onToolbarBackgroundComposeColor

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Center
  ) {
    val marqueeModifier = if (scrollableTitle) {
      Modifier.basicMarquee(
        iterations = Int.MAX_VALUE,
        repeatDelayMillis = 3_000
      )
    } else {
      Modifier
    }

    KurobaComposeText(
      modifier = Modifier
        .wrapContentWidth()
        .wrapContentHeight()
        .then(marqueeModifier),
      text = title.resolve(),
      fontSize = 18.ktu,
      color = textColor,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = if (scrollableTitle) {
        TextOverflow.Clip
      } else {
        TextOverflow.Ellipsis
      }
    )

    if (subtitle != null) {
      Spacer(modifier = Modifier.height(2.dp))

      var inlineContent by remember { mutableStateOf<Map<String, InlineTextContent>>(emptyMap()) }

      val subtitleText = subtitle.resolve()

      ResolveInlinedContent(
        fontSize = 14.ktu,
        subtitleText = subtitleText,
        onInlineContentReady = { newInlineContent -> inlineContent = newInlineContent }
      )

      KurobaComposeText(
        modifier = Modifier
          .wrapContentWidth()
          .wrapContentHeight()
          .then(marqueeModifier),
        text = subtitleText,
        fontSize = 14.ktu,
        color = textColor,
        fontWeight = FontWeight.Light,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inlineContent
      )
    }
  }
}

@Composable
internal fun ToolbarClickableIcon(
  toolbarMenuItem: ToolbarMenuItem,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  chanTheme: ChanTheme,
  onClick: () -> Unit
) {
  val visible by toolbarMenuItem.visibleState
  if (!visible) {
    return
  }

  val drawableId by toolbarMenuItem.drawableIdState

  val alpha = if (enabled) DefaultAlpha else ContentAlpha.disabled

  val rotationAnimatable = remember { Animatable(initialValue = 0f) }

  LaunchedEffect(key1 = toolbarMenuItem) {
    toolbarMenuItem.spinEventsFlow
      .onEach {
        try {
          try {
            rotationAnimatable.animateTo(1f, animationSpec = tween(durationMillis = 750))
          } finally {
            rotationAnimatable.snapTo(0f)
          }
        } catch (_: CancellationException) {
          // no-op
        }
      }
      .collect()
  }

  val clickModifier = if (enabled) {
    Modifier.kurobaClickable(
      bounded = false,
      onClick = { onClick() }
    )
  } else {
    Modifier
  }

  Image(
    modifier = clickModifier
      .then(modifier)
      .graphicsLayer {
        rotationZ = rotationAnimatable.value * 360f
      },
    painter = painterResource(id = drawableId),
    colorFilter = ColorFilter.tint(chanTheme.onToolbarBackgroundComposeColor),
    alpha = alpha,
    contentDescription = null
  )
}

@Composable
fun SearchIcon(
  searchQueryState: TextFieldState,
  onCloseSearchToolbarButtonClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val searchQuery = searchQueryState.collectText()
  val iconTintColor = chanTheme.toolbarBackgroundComposeColor.resolveIconTintColor()

  AnimatedContent(targetState = searchQuery.isEmpty()) { searchQueryEmpty ->
    if (searchQueryEmpty) {
      KurobaComposeClickableIcon(
        drawableId = R.drawable.ic_arrow_back_white_24dp,
        iconTint = IconTint.TintWithColor(iconTintColor),
        onClick = onCloseSearchToolbarButtonClicked
      )
    } else {
      KurobaComposeClickableIcon(
        drawableId = R.drawable.ic_baseline_clear_24,
        iconTint = IconTint.TintWithColor(iconTintColor),
        onClick = { searchQueryState.edit { clearText() } }
      )
    }
  }
}

@Composable
fun SearchToolbarInfoText(
  totalFoundItems: Int,
  currentSearchItemIndex: Int,
  onShowFoundItemsAsPopupClicked: () -> Unit
) {
  if (totalFoundItems < 0) {
    return
  }

  val chanTheme = LocalChanTheme.current

  Box(
    modifier = Modifier
      .fillMaxHeight()
      .wrapContentWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .kurobaClickable(
        enabled = totalFoundItems > 0 && currentSearchItemIndex >= 0,
        bounded = false,
        onClick = { onShowFoundItemsAsPopupClicked() }
      ),
    contentAlignment = Alignment.Center
  ) {
    val textColor = ThemeEngine.resolveTextColor(chanTheme.toolbarBackgroundComposeColor)
    val text = if (totalFoundItems == 0 || currentSearchItemIndex < 0) {
      "--"
    } else {
      "${currentSearchItemIndex + 1}/${totalFoundItems}"
    }

    KurobaComposeText(
      text = text,
      color = textColor
    )
  }
}