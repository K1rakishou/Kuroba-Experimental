package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme

@Composable
fun KurobaComposeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonContent: @Composable RowScope.() -> Unit
) {
    val chanTheme = LocalChanTheme.current

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .wrapContentWidth()
            .height(36.dp)
            .then(modifier),
        content = buttonContent,
        colors = chanTheme.buttonColors()
    )
}
