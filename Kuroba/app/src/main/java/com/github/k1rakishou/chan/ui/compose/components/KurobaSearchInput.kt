package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.clearFocusOnKeyboardDismiss
import com.github.k1rakishou.chan.ui.compose.clearText
import com.github.k1rakishou.chan.ui.compose.forEachTextValue
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.OverrideChanTheme
import com.github.k1rakishou.chan.ui.compose.textAsFlow
import com.github.k1rakishou.core_themes.ChanTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun KurobaSearchInput(
  modifier: Modifier = Modifier,
  displayClearButton: Boolean = true,
  color: Color,
  searchQueryState: TextFieldState
) {
  val chanTheme = LocalChanTheme.current

  var searchQueryIsEmpty by remember { mutableStateOf(true) }

  LaunchedEffect(key1 = searchQueryState) {
    searchQueryState.textAsFlow()
      .onEach { query -> searchQueryIsEmpty = query.isEmpty() }
      .collect()
  }

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    AnimatedVisibility(
      visible = displayClearButton && !searchQueryIsEmpty,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      KurobaComposeClickableIcon(
        modifier = Modifier
          .size(32.dp),
        drawableId = R.drawable.ic_clear_white_24dp,
        iconTint = IconTint.TintWithColor(color),
        onClick = { searchQueryState.edit { clearText() } }
      )
    }

    Box(
      modifier = Modifier
        .wrapContentHeight()
        .weight(1f)
        .align(Alignment.CenterVertically)
        .padding(horizontal = 4.dp)
    ) {
      OverrideChanTheme(
        chanTheme = chanTheme.overrideForSearchInputOnToolbar(
          newAccentColor = color,
          newTextColorPrimary = color
        )
      ) {
        var isSearchQueryEmpty by remember { mutableStateOf(true) }

        LaunchedEffect(key1 = Unit) {
          searchQueryState.forEachTextValue { textFieldCharSequence ->
            isSearchQueryEmpty = textFieldCharSequence.isEmpty()
          }
        }

        Box(
          contentAlignment = Alignment.CenterStart
        ) {
          TextFieldWithHint(
            modifier = Modifier.clearFocusOnKeyboardDismiss(),
            isSearchQueryEmpty = isSearchQueryEmpty,
            chanTheme = chanTheme,
            searchQueryState = searchQueryState,
            textColor = color
          )
        }
      }
    }
  }
}

@Composable
private fun TextFieldWithHint(
  modifier: Modifier = Modifier,
  isSearchQueryEmpty: Boolean,
  chanTheme: ChanTheme,
  searchQueryState: TextFieldState,
  textColor: Color
) {
  KurobaComposeTextFieldV2(
    modifier = modifier.then(
      Modifier
        .wrapContentHeight()
        .fillMaxWidth()
    ),
    state = searchQueryState,
    fontSize = 16.ktu,
    textStyle = remember(key1 = textColor) { TextStyle.Default.copy(color = textColor) },
    lineLimits = TextFieldLineLimits.SingleLine,
    label = null
  )

  AnimatedVisibility(
    modifier = Modifier.padding(start = 8.dp),
    visible = isSearchQueryEmpty,
    enter = fadeIn(),
    exit = fadeOut()
  ) {
    KurobaComposeText(
      text = stringResource(id = R.string.type_to_search_hint),
      color = remember(chanTheme) { textColor.copy(alpha = 0.7f) }
    )
  }
}