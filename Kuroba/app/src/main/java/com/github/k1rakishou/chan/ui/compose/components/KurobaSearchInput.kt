package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

@Composable
fun KurobaSearchInput(
  modifier: Modifier = Modifier,
  chanTheme: ChanTheme,
  onBackgroundColor: Color,
  searchQueryState: MutableState<String>,
  onSearchQueryChanged: (String) -> Unit,
  labelText: String = stringResource(id = R.string.search_hint)
) {
  var localQuery by remember { searchQueryState }

  Row(modifier = modifier) {
    Row(modifier = Modifier.wrapContentHeight()) {
      Box(
        modifier = Modifier
          .wrapContentHeight()
          .weight(1f)
          .align(Alignment.CenterVertically)
          .padding(horizontal = 4.dp)
      ) {
        val interactionSource = remember { MutableInteractionSource() }

        val textColor = remember(key1 = onBackgroundColor) {
          if (ThemeEngine.isDarkColor(onBackgroundColor)) {
            Color.White
          } else {
            Color.Black
          }
        }

        KurobaComposeCustomTextField(
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
          textColor = textColor,
          parentBackgroundColor = onBackgroundColor,
          fontSize = 16.ktu,
          singleLine = true,
          maxLines = 1,
          value = localQuery,
          labelText = labelText,
          onValueChange = { newValue ->
            localQuery = newValue
            onSearchQueryChanged(newValue)
          },
          interactionSource = interactionSource
        )
      }

      AnimatedVisibility(
        visible = localQuery.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        KurobaComposeIcon(
          modifier = Modifier
            .align(Alignment.CenterVertically)
            .kurobaClickable(
              bounded = false,
              onClick = {
                localQuery = ""
                onSearchQueryChanged("")
              }
            ),
          drawableId = R.drawable.ic_clear_white_24dp,
          iconTint = IconTint.TintWithColor(chanTheme.primaryColorCompose)
        )
      }
    }
  }
}