package com.github.k1rakishou.chan.ui.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

val LocalChanTheme = staticCompositionLocalOf<ChanTheme> { error("Theme not provided") }

@Composable
fun ProvideChanTheme(
  themeEngine: ThemeEngine,
  content: @Composable () -> Unit
) {
  var chanTheme by remember { mutableStateOf(themeEngine.chanTheme) }

  DisposableEffect(themeEngine.chanTheme) {
    val themeUpdateObserver = object : ThemeEngine.ThemeChangesListener {
      override fun onThemeChanged() {
        chanTheme = themeEngine.chanTheme.fullCopy()
      }
    }

    themeEngine.addListener(themeUpdateObserver)
    onDispose { themeEngine.removeListener(themeUpdateObserver) }
  }

  CompositionLocalProvider(LocalChanTheme provides chanTheme) {
    val originalColors = MaterialTheme.colors

    val updatedColors = remember(key1 = themeEngine.chanTheme) {
      originalColors.copy(
        primary = chanTheme.primaryColorCompose,
        error = chanTheme.errorColorCompose
      )
    }

    MaterialTheme(colors = updatedColors) {
      ProvideKurobaViewConfiguration {
        content()
      }
    }
  }
}