package com.github.k1rakishou.chan.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

@Composable
fun KurobaComposeIcon(
    modifier: Modifier = Modifier,
    @DrawableRes drawableId: Int,
    colorBehindIcon: Color? = null
) {
    val chanTheme = LocalChanTheme.current
    val tintColor = remember(key1 = chanTheme) {
        if (colorBehindIcon == null) {
            Color(ThemeEngine.resolveDrawableTintColor(chanTheme))
        } else {
            Color(ThemeEngine.resolveDrawableTintColor(ThemeEngine.isDarkColor(colorBehindIcon.value)))
        }
    }

    Image(
        modifier = modifier,
        painter = painterResource(id = drawableId),
        colorFilter = ColorFilter.tint(tintColor),
        contentDescription = null
    )
}