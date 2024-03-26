package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu

@Composable
internal fun PresolveCaptchaButton(
  iconSize: Dp,
  padding: Dp,
  replyLayoutViewModel: ReplyLayoutViewModel,
  onPresolveCaptchaButtonClicked: () -> Unit
) {
  val captchaCounter by replyLayoutViewModel.captchaHolderCaptchaCounterUpdatesFlow.collectAsState(initial = 0)

  Box {
    KurobaComposeIcon(
      modifier = Modifier
        .size(iconSize)
        .padding(padding)
        .kurobaClickable(
          bounded = false,
          onClick = onPresolveCaptchaButtonClicked
        ),
      drawableId = R.drawable.ic_captcha_24dp,
      iconTint = IconTint.DoNotTint
    )

    if (captchaCounter > 0) {
      Box(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.TopEnd)
          .drawBehind {
            drawRoundRect(
              color = Color.Black.copy(alpha = 0.8f),
              cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx())
            )
          }
          .padding(horizontal = 4.dp)
      ) {
        KurobaComposeText(
          text = captchaCounter.toString(),
          color = Color.White,
          fontSize = 11.ktu.fixedSize()
        )
      }
    }
  }
}
