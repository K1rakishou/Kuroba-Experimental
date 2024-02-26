package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ButtonElevation
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun KurobaComposeButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource? = null,
  elevation: ButtonElevation? = ButtonDefaults.elevation(),
  shape: Shape = MaterialTheme.shapes.small,
  border: BorderStroke? = null,
  buttonColors: ButtonColors? = null,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  buttonContent: @Composable RowScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current

  @Suppress("NAME_SHADOWING")
  val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
  val colors = buttonColors ?: chanTheme.buttonColors()
  val contentColor by colors.contentColor(enabled)

  Surface(
    onClick = onClick,
    modifier = modifier.semantics { role = Role.Button },
    enabled = enabled,
    shape = shape,
    color = colors.backgroundColor(enabled).value,
    contentColor = contentColor.copy(alpha = 1f),
    border = border,
    elevation = elevation?.elevation(enabled, interactionSource)?.value ?: 0.dp,
    interactionSource = interactionSource
  ) {
    CompositionLocalProvider(LocalContentAlpha provides contentColor.alpha) {
      ProvideTextStyle(
        value = MaterialTheme.typography.button
      ) {
        Row(
          Modifier
            .defaultMinSize(
              minWidth = KurobaComposeDefaults.Button.MinWidth,
              minHeight = KurobaComposeDefaults.Button.MinHeight
            )
            .padding(contentPadding),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
          content = buttonContent
        )
      }
    }
  }
}