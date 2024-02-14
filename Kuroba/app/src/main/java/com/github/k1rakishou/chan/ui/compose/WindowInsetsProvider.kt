package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager

val LocalWindowInsets = compositionLocalOf<KurobaWindowInsets> { error("LocalWindowInsets not initialized") }

@Composable
fun ProvideWindowInsets(
    globalWindowInsetsManager: GlobalWindowInsetsManager,
    content: @Composable () -> Unit
) {
    val currentWindowInsets by globalWindowInsetsManager.currentWindowInsets

    CompositionLocalProvider(LocalWindowInsets provides currentWindowInsets) {
        content()
    }
}

@Immutable
data class KurobaWindowInsets(
    val left: Dp = 0.dp,
    val right: Dp = 0.dp,
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val keyboardOpened: Boolean = false
) {

    fun asPaddingValues(
        consumeLeft: Boolean = false,
        consumeRight: Boolean = false,
        consumeTop: Boolean = false,
        consumeBottom: Boolean = false,
    ): PaddingValues {
        return PaddingValues(
            start = left.takeUnless { consumeLeft } ?: 0.dp,
            end = right.takeUnless { consumeRight } ?: 0.dp,
            top = top.takeUnless { consumeTop } ?: 0.dp,
            bottom = bottom.takeUnless { consumeBottom } ?: 0.dp
        )
    }

    fun copyInsets(
        newLeft: Dp = left,
        newRight: Dp = right,
        newTop: Dp = top,
        newBottom: Dp = bottom,
    ): KurobaWindowInsets {
        return KurobaWindowInsets(
            left = newLeft,
            right = newRight,
            top = newTop,
            bottom = newBottom,
            keyboardOpened = keyboardOpened
        )
    }

}