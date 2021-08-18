package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.accompanist.insets.ProvideWindowInsets
import javax.inject.Inject

abstract class BaseFloatingComposeController(
  context: Context
) : Controller(context) {
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private var presenting = true

  @OptIn(ExperimentalMaterialApi::class)
  override fun onCreate() {
    super.onCreate()
    presenting = true

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          ProvideWindowInsets {
            val currentPaddings by globalWindowInsetsManager.currentInsetsCompose
            val backgroundColor = remember {
              Color(red = 0f, green = 0f, blue = 0f, alpha = 0.6f)
            }

            Surface(
              onClick = { pop() },
              indication = null,
              color = backgroundColor,
              modifier = Modifier.fillMaxSize()
            ) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(
                    start = currentPaddings.calculateStartPadding(LayoutDirection.Ltr) + HORIZ_PADDING,
                    end = currentPaddings.calculateEndPadding(LayoutDirection.Ltr) + HORIZ_PADDING,
                    top = currentPaddings.calculateTopPadding() + VERT_PADDING,
                    bottom = currentPaddings.calculateBottomPadding() + VERT_PADDING,
                  )
              ) {
                BuildContent()
              }
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
    val VERT_PADDING = 16.dp
    val HORIZ_PADDING = 16.dp
  }
}