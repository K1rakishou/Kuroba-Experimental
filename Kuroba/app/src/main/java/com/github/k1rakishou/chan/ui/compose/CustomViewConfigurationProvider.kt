package com.github.k1rakishou.chan.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@Composable
fun ProvideKurobaViewConfiguration(
  content: @Composable () -> Unit
) {
  val context = LocalContext.current

  val kurobaViewConfiguration = remember {
    KurobaViewConfiguration(android.view.ViewConfiguration.get(context))
  }

  CompositionLocalProvider(LocalViewConfiguration provides kurobaViewConfiguration) {
    content()
  }
}

class KurobaViewConfiguration(
  private val viewConfiguration: android.view.ViewConfiguration
) : ViewConfiguration {
  override val longPressTimeoutMillis: Long
    get() = android.view.ViewConfiguration.getLongPressTimeout().toLong()
  override val doubleTapTimeoutMillis: Long
    get() = android.view.ViewConfiguration.getDoubleTapTimeout().toLong()
  override val doubleTapMinTimeMillis: Long
    get() = 40
  override val touchSlop: Float
    get() = viewConfiguration.scaledTouchSlop.toFloat()
  override val minimumTouchTargetSize: DpSize
    get() = DpSize(20.dp, 20.dp)
}