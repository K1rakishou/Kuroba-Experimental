package com.github.k1rakishou.chan.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.res.painterResource
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.core_themes.resolveIconTintColor

@Composable
fun KurobaComposeClickableIcon(
  @DrawableRes drawableId: Int,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  iconTint: IconTint = IconTint.Tint,
  onClick: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val colorFilter = remember(key1 = chanTheme, key2 = iconTint) {
    return@remember when (iconTint) {
      is IconTint.DoNotTint -> null
      is IconTint.Tint -> {
        ColorFilter.tint(chanTheme.backColorCompose.resolveIconTintColor())
      }
      is IconTint.TintWithColor -> {
        ColorFilter.tint(iconTint.color)
      }
    }
  }

  val alpha = if (enabled) DefaultAlpha else ContentAlpha.disabled

  val clickModifier = if (enabled) {
    Modifier.kurobaClickable(
      bounded = false,
      onClick = { onClick() }
    )
  } else {
    Modifier
  }

  Image(
    modifier = clickModifier.then(modifier),
    painter = painterResource(id = drawableId),
    colorFilter = colorFilter,
    alpha = alpha,
    contentDescription = null
  )
}
