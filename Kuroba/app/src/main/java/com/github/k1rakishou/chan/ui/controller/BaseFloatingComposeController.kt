package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
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

  open val contentAlignment: Alignment = Alignment.TopStart

  override fun onCreate() {
    super.onCreate()
    presenting = true

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          val currentPaddings by globalWindowInsetsManager.currentInsetsCompose
          val backgroundColor = remember { Color(red = 0f, green = 0f, blue = 0f, alpha = 0.6f) }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .drawBehind { drawRect(backgroundColor) }
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { pop() }
              ),
            contentAlignment = Alignment.Center
          ) {
            val horizPadding = remember {
              if (AppModuleAndroidUtils.isTablet()) {
                HPADDING_TABLET_COMPOSE
              } else {
                HPADDING_COMPOSE
              }
            }

            val vertPadding = remember {
              if (AppModuleAndroidUtils.isTablet()) {
                VPADDING_TABLET_COMPOSE
              } else {
                VPADDING_COMPOSE
              }
            }

            Box(
              modifier = Modifier
                .padding(
                  start = currentPaddings.calculateStartPadding(LayoutDirection.Ltr) + horizPadding,
                  end = currentPaddings.calculateEndPadding(LayoutDirection.Ltr) + horizPadding,
                  top = currentPaddings.calculateTopPadding() + vertPadding,
                  bottom = currentPaddings.calculateBottomPadding() + vertPadding,
                ),
              contentAlignment = contentAlignment,
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