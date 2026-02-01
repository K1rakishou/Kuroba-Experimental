package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowInsets
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowSizeClass
import com.github.k1rakishou.chan.ui.compose.window.KurobaWindowWidthSizeClass
import com.github.k1rakishou.chan.ui.compose.window.WindowWidthSizeClass
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

abstract class BaseFloatingComposeController(
  context: Context
) : Controller(context) {
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private var presenting = true

  open val contentAlignment: Alignment = Alignment.Center
  open val closableByClickingOutside = true
  open val currentlyInvisible = mutableStateOf(false)

  override fun onCreate() {
    super.onCreate()
    presenting = true

    view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
          val windowInsets = LocalWindowInsets.current
          val windowSizeClass = LocalWindowSizeClass.current
          val backgroundColor = remember { Color(red = 0f, green = 0f, blue = 0f, alpha = 0.6f) }
          val invisible by currentlyInvisible

          val additionalModifier = if (invisible) {
            Modifier
              .pointerInteropFilter(
                onTouchEvent = { _ -> false }
              )
              .graphicsLayer {
                alpha = 0f
              }
          } else {
            Modifier
              .drawBehind { drawRect(backgroundColor) }
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { pop() }
              )
          }

          BoxWithConstraints(
            modifier = Modifier
              .fillMaxSize()
              .then(additionalModifier),
            contentAlignment = Alignment.Center
          ) {
            val availableWidth = maxWidth

            val horizPadding = remember {
              if (AppModuleAndroidUtils.isTablet) {
                HPADDING_TABLET_COMPOSE
              } else {
                HPADDING_COMPOSE
              }
            }

            val vertPadding = remember {
              if (AppModuleAndroidUtils.isTablet) {
                VPADDING_TABLET_COMPOSE
              } else {
                VPADDING_COMPOSE
              }
            }

            val maxControllerWidth = when (windowSizeClass.widthSizeClass.asKurobaWindowWidthSizeClass()) {
              KurobaWindowWidthSizeClass.Compact -> {
                availableWidth.coerceAtMost(WindowWidthSizeClass.CompactWindowMaxWidth.dp)
              }
              KurobaWindowWidthSizeClass.Medium -> {
                availableWidth.coerceAtMost(WindowWidthSizeClass.MediumWindowMaxWidth.dp)
              }
              KurobaWindowWidthSizeClass.Expanded -> {
                availableWidth.coerceAtMost(WindowWidthSizeClass.MediumWindowMaxWidth.dp)
              }
            }

            Box(
              modifier = Modifier
                .widthIn(min = 128.dp, max = maxControllerWidth)
                .padding(
                  start = windowInsets.left + horizPadding,
                  end = windowInsets.right + horizPadding,
                  top = windowInsets.top + vertPadding,
                  bottom = windowInsets.bottom + vertPadding,
                ),
              contentAlignment = Alignment.Center,
            ) {
              BuildContent()
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    presenting = false
  }

  override fun onBack(): Boolean {
    if (presenting) {
      if (pop()) {
        return true
      }
    }

    return super.onBack()
  }

  protected open fun onOutsideOfDialogClicked() {
    if (closableByClickingOutside) {
      pop()
    }
  }

  protected open fun pop(): Boolean {
    if (!presenting) {
      return false
    }

    presenting = false
    stopPresenting()

    return true
  }

  @Composable
  abstract fun BoxScope.BuildContent()

  companion object {
    val HPADDING_COMPOSE = 12.dp
    val VPADDING_COMPOSE = 16.dp

    val HPADDING_TABLET_COMPOSE = 32.dp
    val VPADDING_TABLET_COMPOSE = 48.dp
  }
}