package com.github.k1rakishou.chan.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

@Composable
fun KurobaComposeIcon(
  modifier: Modifier = Modifier,
  @DrawableRes drawableId: Int,
  enabled: Boolean = true,
  iconTint: IconTint = IconTint.Tint
) {
  val chanTheme = LocalChanTheme.current

  val colorFilter = remember(key1 = chanTheme, key2 = iconTint) {
    return@remember when (iconTint) {
      is IconTint.DoNotTint -> null
      is IconTint.Tint -> {
        val tintColor = Color(ThemeEngine.resolveDrawableTintColor(chanTheme))
        ColorFilter.tint(tintColor)
      }
      is IconTint.TintWithColor -> {
        val tintColor = iconTint.color
        ColorFilter.tint(tintColor)
      }
    }
  }

  val alpha = if (enabled) ContentAlpha.high else ContentAlpha.disabled

  Image(
    modifier = modifier,
    painter = painterResource(id = drawableId),
    colorFilter = colorFilter,
    alpha = alpha,
    contentDescription = null
  )
}

@Immutable
sealed interface IconTint {
  data object Tint : IconTint
  data class TintWithColor(val color: Color) : IconTint
  data object DoNotTint : IconTint
}