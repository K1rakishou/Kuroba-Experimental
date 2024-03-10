package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu

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
      .wrapContentHeight(),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    postFormatterButtons.forEach { postFormatterButton ->
      key(postFormatterButton.openTag) {
        KurobaComposeCardView(
          modifier = Modifier
            .wrapContentHeight()
            .wrapContentWidth()
            .kurobaClickable(
              enabled = replyLayoutEnabled,
              bounded = true,
              onClick = { replyLayoutState.insertTags(postFormatterButton) }
            )
            .padding(vertical = 6.dp, horizontal = 10.dp),
          shape = remember { RoundedCornerShape(4.dp) }
        ) {
          KurobaComposeText(
            text = postFormatterButton.title,
            fontSize = 14.ktu.fixedSize()
          )
        }
      }
    }
  }
}