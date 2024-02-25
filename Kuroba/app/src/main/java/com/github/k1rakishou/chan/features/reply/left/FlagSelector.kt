package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.runtime.Composable
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
internal fun FlagSelector(
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  replyLayoutEnabled: Boolean,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  // TODO: New reply layout

//    var flags by remember { mutableStateOf<List<BoardFlag>>(emptyList()) }
//
//    LaunchedEffect(
//        key1 = Unit,
//        block = {
//            flags = loadChanCatalog.await(replyLayoutState.chanDescriptor).getOrNull()
//                ?.flags
//                ?: emptyList()
//        }
//    )
//
//    if (flags.isEmpty()) {
//        return
//    }
//
//    var lastUsedFlagMut by remember { mutableStateOf<BoardFlag?>(null) }
//    val lastUsedFlag = lastUsedFlagMut

//    LaunchedEffect(
//        key1 = Unit,
//        block = {
//            lastUsedFlagMut = replyLayoutState.flag.value
//
//            snapshotFlow { replyLayoutState.flag.value }
//                .collect { newSelectedFlag -> lastUsedFlagMut = newSelectedFlag }
//        }
//    )
//
//    if (lastUsedFlag == null) {
//        return
//    }
//
//    val lastUsedFlagText = remember(key1 = lastUsedFlag) { lastUsedFlag.asUserReadableString() }
//    val flagSelectorAlpha = if (replyLayoutEnabled) 1f else ContentAlpha.disabled
//
//    Spacer(modifier = Modifier.height(16.dp))
//
//    KurobaComposeText(
//        modifier = Modifier.padding(start = 6.dp),
//        color = chanTheme.textColorHint,
//        text = stringResource(id = R.string.reply_flag_label_text)
//    )
//
//    Spacer(modifier = Modifier.height(8.dp))
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(42.dp)
//            .drawBehind {
//                drawRoundRect(
//                    color = chanTheme.backColorSecondary,
//                    topLeft = Offset.Zero,
//                    size = Size(
//                        width = this.size.width,
//                        height = this.size.height
//                    ),
//                    alpha = 0.4f,
//                    cornerRadius = CornerRadius(4.dp.toPx())
//                )
//            }
//            .kurobaClickable(
//                enabled = replyLayoutEnabled,
//                bounded = true,
//                onClick = { onFlagSelectorClicked(replyLayoutState.chanDescriptor) }
//            )
//            .padding(horizontal = 8.dp, vertical = 2.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        KurobaComposeText(
//            modifier = Modifier
//                .weight(1f)
//                .wrapContentHeight()
//                .graphicsLayer { alpha = flagSelectorAlpha },
//            text = lastUsedFlagText,
//            fontSize = 16.sp
//        )
//
//        Spacer(modifier = Modifier.width(8.dp))
//
//        KurobaComposeIcon(drawableId = R.drawable.ic_baseline_arrow_drop_down_24)
//
//        Spacer(modifier = Modifier.width(8.dp))
//    }
//
//    Spacer(modifier = Modifier.height(4.dp))
}