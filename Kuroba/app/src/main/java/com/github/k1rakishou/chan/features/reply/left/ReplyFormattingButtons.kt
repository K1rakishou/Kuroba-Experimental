package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.ktu

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReplyFormattingButtons(
  replyLayoutEnabled: Boolean,
  replyLayoutState: ReplyLayoutState
) {
  val postFormatterButtons by replyLayoutState.postFormatterButtons
  if (postFormatterButtons.isEmpty()) {
    return
  }

  FlowRow(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
  ) {
    postFormatterButtons.forEach { postFormatterButton ->
      key(postFormatterButton.openTag) {
        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentHeight()
            .wrapContentWidth(),
          enabled = replyLayoutEnabled,
          text = postFormatterButton.title,
          fontSize = 14.ktu,
          onClick = { replyLayoutState.insertTags(postFormatterButton) }
        )
      }
    }
  }
}