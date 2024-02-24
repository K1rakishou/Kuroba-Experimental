package com.github.k1rakishou.chan.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.res.painterResource
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

@Composable
fun KurobaComposeClickableIcon(
    @DrawableRes drawableId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorBehindIcon: Color? = null,
    onClick: () -> Unit
) {
    val chanTheme = LocalChanTheme.current
    val tintColor = remember(key1 = chanTheme) {
        if (colorBehindIcon == null) {
            Color(ThemeEngine.resolveDrawableTintColor(chanTheme))
        } else {
            Color(ThemeEngine.resolveDrawableTintColor(ThemeEngine.isDarkColor(colorBehindIcon.value)))
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
        colorFilter = ColorFilter.tint(tintColor),
        alpha = alpha,
        contentDescription = null
    )
}
