package com.github.k1rakishou.chan.ui.compose.providers

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.window.layout.WindowMetricsCalculator
import com.github.k1rakishou.chan.ui.compose.window.WindowSizeClass
import com.github.k1rakishou.chan.utils.appDependencies

val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> { error("LocalWindowSizeClass not initialized") }

@Composable
fun ProvideWindowClassSize(content: @Composable () -> Unit) {
  val globalUiStateHolder = appDependencies().globalUiStateHolder
  val context = LocalContext.current

  @Suppress("UNUSED_VARIABLE") val configuration = LocalConfiguration.current

  val density = LocalDensity.current
  val metrics = remember(key1 = configuration, key2 = density) {
    WindowMetricsCalculator.getOrCreate()
      .computeCurrentWindowMetrics(context as ComponentActivity)
  }

  val size = remember(key1 = metrics) {
    metrics.bounds.toComposeRect().size
  }

  val windowClassSize = remember(key1 = size) {
    val dpSize = with(density) { size.toDpSize() }
    WindowSizeClass.calculateFromSize(dpSize)
  }

  LaunchedEffect(key1 = windowClassSize, key2 = size) {
    globalUiStateHolder.updateMainUiState {
      updateWindowSizeClass(windowClassSize)
      updateWindowSize(IntSize(size.width.toInt(), size.height.toInt()))
    }
  }

  CompositionLocalProvider(LocalWindowSizeClass provides windowClassSize) {
    content()
  }

}