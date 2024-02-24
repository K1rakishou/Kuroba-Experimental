package com.github.k1rakishou.chan.ui.compose.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.LocalWindowInsets

@Composable
fun KurobaComposeProgressIndicator(
    modifier: Modifier = DefaultFillMaxSizeModifier,
    overrideColor: Color? = null
) {
    val windowInsets = LocalWindowInsets.current

    Box(
        modifier = modifier.then(
            Modifier
                .padding(bottom = windowInsets.bottom)
        )
    ) {
        val color = if (overrideColor == null) {
            val chanTheme = LocalChanTheme.current
            remember(key1 = chanTheme.accentColor) { Color(chanTheme.accentColor) }
        } else {
            overrideColor
        }

        CircularProgressIndicator(
            color = color,
            modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp, 42.dp)
        )
    }
}

@Composable
fun KurobaComposeProgressIndicator(
    modifier: Modifier = DefaultFillMaxSizeModifier,
    @FloatRange(from = 0.0, to = 1.0) progress: Float,
    overrideColor: Color? = null,
    indicatorSize: Dp = 42.dp
) {
    Box(modifier = modifier) {
        val color = if (overrideColor == null) {
            val chanTheme = LocalChanTheme.current
            chanTheme.accentColorCompose
        } else {
            overrideColor
        }

        CircularProgressIndicator(
            progress = progress,
            color = color,
            modifier = Modifier
                .align(Alignment.Center)
                .size(indicatorSize, indicatorSize)
        )
    }
}