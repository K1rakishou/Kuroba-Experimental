package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
internal fun FlagSelector(
  replyLayoutEnabled: Boolean,
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
  val hasFlagsToShow by replyLayoutState.hasFlagsToShow
  if (!hasFlagsToShow) {
    return
  }

  val chanTheme = LocalChanTheme.current

  var lastUsedFlagMut by remember { mutableStateOf<LoadBoardFlagsUseCase.FlagInfo?>(null) }
  val lastUsedFlag = lastUsedFlagMut

  LaunchedEffect(
    key1 = Unit,
    block = {
      lastUsedFlagMut = replyLayoutState.flag.value

      snapshotFlow { replyLayoutState.flag.value }
        .collect { newSelectedFlag -> lastUsedFlagMut = newSelectedFlag }
    }
  )

  if (lastUsedFlag == null) {
    return
  }

  val lastUsedFlagText = remember(key1 = lastUsedFlag) { lastUsedFlag.asUserReadableString() }
  val flagSelectorAlpha = if (replyLayoutEnabled) ContentAlpha.high else ContentAlpha.disabled

  Spacer(modifier = Modifier.height(16.dp))

  KurobaComposeText(
    modifier = Modifier.padding(start = 6.dp),
    color = chanTheme.textColorHintCompose,
    text = stringResource(id = R.string.reply_flag)
  )

  Spacer(modifier = Modifier.height(8.dp))

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(42.dp)
      .drawBehind {
        drawRoundRect(
          color = chanTheme.backColorSecondaryCompose,
          topLeft = Offset.Zero,
          size = Size(
            width = this.size.width,
            height = this.size.height
          ),
          alpha = 0.4f,
          cornerRadius = CornerRadius(4.dp.toPx())
        )
      }
      .kurobaClickable(
        enabled = replyLayoutEnabled,
        bounded = true,
        onClick = { onFlagSelectorClicked(replyLayoutState.chanDescriptor) }
      )
      .padding(horizontal = 8.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    KurobaComposeText(
      modifier = Modifier
        .weight(1f)
        .wrapContentHeight()
        .graphicsLayer { alpha = flagSelectorAlpha },
      text = lastUsedFlagText,
      fontSize = 16.sp
    )

    Spacer(modifier = Modifier.width(8.dp))

    KurobaComposeIcon(drawableId = R.drawable.ic_baseline_arrow_drop_down_24)

    Spacer(modifier = Modifier.width(8.dp))
  }

  Spacer(modifier = Modifier.height(4.dp))
}