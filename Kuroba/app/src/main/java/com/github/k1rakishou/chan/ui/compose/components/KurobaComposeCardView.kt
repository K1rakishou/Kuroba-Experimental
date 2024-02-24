package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme

@Composable
fun KurobaComposeCardView(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    shape: Shape = RoundedCornerShape(2.dp),
    content: @Composable () -> Unit
) {
    val chanTheme = LocalChanTheme.current

    Card(
        modifier = modifier,
        shape = shape,
        backgroundColor = backgroundColor ?: chanTheme.backColorCompose
    ) {
        content()
    }
}