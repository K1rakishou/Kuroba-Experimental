package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.pxToDp
import javax.inject.Inject

abstract class BaseFloatingComposeController(
  context: Context
) : Controller(context), WindowInsetsListener {
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var presenting = true

  private var paddings = mutableStateOf(PaddingValues())

  override fun onCreate() {
    super.onCreate()
    presenting = true

    view = ComposeView(context).apply {
      setContent {
        val currentPaddings by remember { paddings }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.6f))
        ) {
          Box(
            modifier = Modifier.padding(
              start = currentPaddings.calculateStartPadding(LayoutDirection.Ltr),
              end = currentPaddings.calculateEndPadding(LayoutDirection.Ltr),
              top = currentPaddings.calculateTopPadding(),
              bottom = currentPaddings.calculateBottomPadding()
            )
          ) {
            BuildContent()
          }
        }
      }
    }

    updatePaddings()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    updatePaddings()
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    presenting = false
  }

  private fun updatePaddings() {
    paddings.value = PaddingValues(
      start = pxToDp(globalWindowInsetsManager.left().toFloat()).dp,
      end = pxToDp(globalWindowInsetsManager.right().toFloat()).dp,
      top = pxToDp(globalWindowInsetsManager.top().toFloat()).dp,
      bottom = pxToDp(globalWindowInsetsManager.bottom().toFloat()).dp
    )
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
}