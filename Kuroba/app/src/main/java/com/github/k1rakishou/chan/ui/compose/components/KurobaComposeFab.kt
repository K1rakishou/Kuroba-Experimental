package com.github.k1rakishou.chan.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.core_themes.ThemeEngine

val KurobaComposeFabSize = 52.dp
val KurobaComposeFabMargins = 16.dp

@Composable
fun BoxScope.KurobaComposeFab(
  modifier: Modifier = Modifier,
  controllerKey: ControllerKey,
  @DrawableRes drawableId: Int,
  onClick: () -> Unit
) {
  val contentPaddings = LocalContentPaddings.current
  val chanTheme = LocalChanTheme.current

  FloatingActionButton(
    modifier = modifier
      .then(
        Modifier
          .size(KurobaComposeFabSize)
          .align(Alignment.BottomEnd)
          .offset {
            return@offset IntOffset(
              x = -(KurobaComposeFabMargins.roundToPx()),
              y = -(contentPaddings.calculateBottomPadding(controllerKey) + (KurobaComposeFabMargins / 2)).roundToPx()
            )
          }
      ),
    onClick = onClick,
    backgroundColor = chanTheme.accentColorCompose,
    contentColor = ThemeEngine.resolveTextColor(chanTheme.accentColorCompose),
    content = {
      KurobaComposeIcon(drawableId = drawableId)
    }
  )
}