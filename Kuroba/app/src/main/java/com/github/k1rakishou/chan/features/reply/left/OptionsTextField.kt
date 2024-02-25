package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.components.KurobaLabelText

@Composable
internal fun OptionsTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onMoveFocus: () -> Unit
) {
  val textStyle = remember { TextStyle(fontSize = 16.sp) }
  val labelText = stringResource(id = R.string.reply_options)

  val options by replyLayoutState.options

  KurobaComposeTextField(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
    enabled = replyLayoutEnabled,
    value = options,
    singleLine = true,
    textStyle = textStyle,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Sentences,
      imeAction = ImeAction.Next
    ),
    keyboardActions = KeyboardActions(onNext = { onMoveFocus() }),
    label = { interactionSource ->
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onOptionsChanged(newTextFieldValue) }
  )
}