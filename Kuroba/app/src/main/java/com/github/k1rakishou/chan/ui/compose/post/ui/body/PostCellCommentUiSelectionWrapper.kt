package com.github.k1rakishou.chan.ui.compose.post.ui.body

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.res.ResourcesCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.composecustomtextselection.lib.ConfigurableTextToolbar
import com.github.k1rakishou.composecustomtextselection.lib.SelectableTextContainer
import com.github.k1rakishou.composecustomtextselection.lib.SelectionToolbarMenu
import com.github.k1rakishou.composecustomtextselection.lib.rememberSelectionState
import com.github.k1rakishou.composecustomtextselection.lib.textSelectionAfterDoubleTapOrTapWithLongTap


@Composable
internal fun PostCellCommentSelectionWrapper(
  textSelectionEnabled: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  content: @Composable (Modifier, (TextLayoutResult) -> Unit) -> Unit
) {
  if (!textSelectionEnabled) {
    content(Modifier) {
      // no-op
    }

    return
  }

  val view = LocalView.current
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current
  val selectionState = rememberSelectionState()

  val onCopySelectedTextUpdated by rememberUpdatedState(newValue = onCopySelectedText)
  val onQuoteSelectedTextUpdated by rememberUpdatedState(newValue = onQuoteSelectedText)

  val configurableTextToolbar = remember {
    val resources = context.resources
    val theme = context.theme

    var id = 1
    var order = 0

    val selectionToolbarMenu = SelectionToolbarMenu(
      items = listOf(
        SelectionToolbarMenu.Item(
          id = id++,
          order = order++,
          text = resources.getString(R.string.post_copy),
          icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_content_copy_24, theme),
          callback = { selectedText -> onCopySelectedTextUpdated.invoke(selectedText.text) }
        ),
        SelectionToolbarMenu.Item(
          id = id++,
          order = order++,
          text = resources.getString(R.string.post_quote),
          icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_format_quote_24, theme),
          callback = { selectedText -> onQuoteSelectedTextUpdated.invoke(true, selectedText.text) }
        ),
      )
    )

    return@remember ConfigurableTextToolbar(
      view = view,
      selectionToolbarMenu = selectionToolbarMenu
    )
  }

  val textSelectionColors = remember(key1 = chanTheme.accentColorCompose) {
    TextSelectionColors(
      handleColor = chanTheme.accentColorCompose,
      backgroundColor = chanTheme.accentColorCompose.copy(alpha = 0.4f)
    )
  }

  CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
    SelectableTextContainer(
      modifier = Modifier
        .pointerInput(
          key1 = Unit,
          block = { textSelectionAfterDoubleTapOrTapWithLongTap(selectionState) }
        ),
      selectionState = selectionState,
      configurableTextToolbar = configurableTextToolbar,
      onEnteredSelection = { onTextSelectionModeChanged(true) },
      onExitedSelection = { onTextSelectionModeChanged(false) },
      textContent = { modifier, onTextLayout -> content(modifier, onTextLayout) }
    )
  }
}