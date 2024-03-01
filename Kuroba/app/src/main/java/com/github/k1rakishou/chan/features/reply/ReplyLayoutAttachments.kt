package com.github.k1rakishou.chan.features.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.data.ReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger

private const val COIL_FAILED_TO_DECODE_FRAME_ERROR_MSG =
  "Often this means BitmapFactory could not decode the image data read from the input source"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReplyAttachments(
  replyLayoutEnabled: Boolean,
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  onAttachedMediaClicked: (ReplyAttachable) -> Unit,
  onRemoveAttachedMediaClicked: (ReplyAttachable) -> Unit
) {
  val paddings = 8.dp
  val attachedMediaList by replyLayoutState.attachables
  if (attachedMediaList.attachables.isEmpty()) {
    return
  }

  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility

  val additionalModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier.wrapContentHeight()
  } else {
    val scrollState = rememberScrollState()

    Modifier
      .height(90.dp)
      .verticalScroll(state = scrollState)
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .then(additionalModifier)
  ) {
    val mediaHeight = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) 120.dp else 80.dp

    val mediaWidth = if (this.maxWidth > 250.dp) {
      (this.maxWidth - paddings) / 2
    } else {
      this.maxWidth
    }

    FlowRow(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      attachedMediaList.attachables.forEach { attachedMedia ->
        key(attachedMedia.key) {
          when (attachedMedia) {
            is ReplyAttachable.ReplyFileAttachable -> {
              AttachedMediaThumbnail(
                replyLayoutViewModel = replyLayoutViewModel,
                replyLayoutEnabled = replyLayoutEnabled,
                replyFileAttachable = attachedMedia,
                mediaWidth = mediaWidth,
                mediaHeight = mediaHeight,
                paddings = paddings,
                onAttachedMediaClicked = onAttachedMediaClicked,
                onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked
              )
            }
            is ReplyAttachable.ReplyTooManyAttachables -> {
              // TODO: New reply layout
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(Color.Green.copy(alpha = 0.5f))
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AttachedMediaThumbnail(
  replyLayoutViewModel: ReplyLayoutViewModel,
  replyLayoutEnabled: Boolean,
  replyFileAttachable: ReplyAttachable.ReplyFileAttachable,
  mediaWidth: Dp,
  mediaHeight: Dp,
  paddings: Dp,
  onAttachedMediaClicked: (ReplyAttachable) -> Unit,
  onRemoveAttachedMediaClicked: (ReplyAttachable) -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current

  val mediaHeightPx = with(density) { mediaHeight.roundToPx() - 8.dp.roundToPx() }
  val alpha = if (replyLayoutEnabled) ContentAlpha.high else ContentAlpha.disabled

  Box(
    modifier = Modifier
      .width(mediaWidth)
      .height(mediaHeight)
  ) {
    val imageRequest by produceState<ImageRequest?>(
      key1 = replyFileAttachable,
      key2 = mediaHeightPx,
      initialValue = null
    ) {
      when (val replyFileResult = replyLayoutViewModel.getReplyFileByUuid(replyFileAttachable.fileUuid)) {
        is ModularResult.Error -> {
          // TODO: New reply layout.
          TODO("Handle error case")
        }
        is ModularResult.Value -> {
          value = ImageRequest.Builder(context)
            .data(replyFileResult.value.fileOnDisk)
            .crossfade(true)
            .size(mediaHeightPx)
            .videoFrameMillis(frameMillis = 1000L)
            .build()
        }
      }
    }

    var imageStateMut by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val imageState = imageStateMut

    Box {
      AsyncImage(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = paddings / 2, vertical = paddings / 2)
          .kurobaClickable(
            enabled = replyLayoutEnabled,
            bounded = true,
            onClick = { onAttachedMediaClicked(replyFileAttachable) }
          ),
        model = imageRequest,
        contentDescription = "Attached media",
        contentScale = ContentScale.Crop,
        alpha = alpha,
        onState = { state -> imageStateMut = state }
      )

      if (imageState is AsyncImagePainter.State.Error) {
        Logger.error("AttachedMediaThumbnail") {
          "AttachedMediaThumbnail() attachedMediaFilePath: ${replyFileAttachable.fileUuid}, " +
            "error: ${imageState.result.throwable.errorMessageOrClassName()}"
        }

        val isFailedToDecodeVideoFrameError = (imageState.result.throwable as? IllegalStateException)
          ?.message
          ?.contains(COIL_FAILED_TO_DECODE_FRAME_ERROR_MSG)
          ?: false

        val drawableId = if (isFailedToDecodeVideoFrameError) {
          R.drawable.ic_baseline_movie_24
        } else {
          R.drawable.ic_baseline_warning_24
        }

        KurobaComposeIcon(
          modifier = Modifier
            .size(24.dp)
            .align(Alignment.Center),
          drawableId = drawableId
        )
      }
    }

    val iconBgColor = remember { Color.Black.copy(alpha = 0.5f) }

    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(end = 4.dp, top = 4.dp)
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .background(
            color = iconBgColor,
            shape = CircleShape
          )
          .kurobaClickable(
            enabled = replyLayoutEnabled,
            bounded = false,
            onClick = { onRemoveAttachedMediaClicked(replyFileAttachable) }
          ),
        enabled = replyLayoutEnabled,
        drawableId = R.drawable.ic_baseline_clear_24
      )
    }
  }
}