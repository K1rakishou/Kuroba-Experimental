package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.GrayscaleTransformation
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.reply.data.SyntheticReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.SyntheticReplyAttachableState
import com.github.k1rakishou.chan.ui.compose.Shimmer
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMiddleEllipsisText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeSelectionIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.util.ChanPostUtils

@Composable
internal fun ReplyAttachments(
  replyLayoutEnabled: Boolean,
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  onAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachedMediaLongClicked: (ReplyFileAttachable) -> Unit,
  onRemoveAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachableSelectionChanged: (ReplyFileAttachable, Boolean) -> Unit,
  onAttachableStatusIconButtonClicked: (ReplyFileAttachable) -> Unit
) {
  val attachedMediaList by replyLayoutState.attachables
  val syntheticAttachables = replyLayoutState.syntheticAttachables

  if (attachedMediaList.attachables.isEmpty() && syntheticAttachables.isEmpty()) {
    return
  }

  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility

  val additionalModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier.wrapContentHeight()
  } else {
    val scrollState = rememberScrollState()

    Modifier
      .height(110.dp)
      .verticalScroll(state = scrollState)
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .then(additionalModifier)
  ) {
    val mediaHeight = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
      160.dp
    } else {
      100.dp
    }

    // TODO: make this number (2) dynamic so that it looks good on wider screens
    val mediaWidth = (this.maxWidth / 2) - 4.dp

    FlowRow(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      syntheticAttachables.forEach { syntheticReplyAttachable ->
        key(syntheticReplyAttachable.id) {
          SyntheticReplyAttachable(
            syntheticReplyAttachable = syntheticReplyAttachable,
            mediaWidth = mediaWidth,
            mediaHeight = mediaHeight
          )
        }
      }

      attachedMediaList.attachables.forEach { attachedMedia ->
        key(attachedMedia.key) {
          AttachedMediaThumbnail(
            replyLayoutViewModel = replyLayoutViewModel,
            replyLayoutEnabled = replyLayoutEnabled,
            replyFileAttachable = attachedMedia,
            mediaWidth = mediaWidth,
            mediaHeight = mediaHeight,
            onAttachedMediaClicked = onAttachedMediaClicked,
            onAttachedMediaLongClicked = onAttachedMediaLongClicked,
            onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
            onAttachableSelectionChanged = onAttachableSelectionChanged,
            onAttachableStatusIconButtonClicked = onAttachableStatusIconButtonClicked
          )
        }
      }
    }
  }
}

@Composable
private fun SyntheticReplyAttachable(
  syntheticReplyAttachable: SyntheticReplyAttachable,
  mediaWidth: Dp,
  mediaHeight: Dp,
) {
  val chanTheme = LocalChanTheme.current

  Box(
    modifier = Modifier
      .width(mediaWidth)
      .height(mediaHeight)
  ) {
    Shimmer(modifier = Modifier.fillMaxSize())

    val text = when (syntheticReplyAttachable.state) {
      SyntheticReplyAttachableState.Initializing -> stringResource(id = R.string.reply_synthetic_file_state_initializing)
      SyntheticReplyAttachableState.Downloading -> stringResource(id = R.string.reply_synthetic_file_state_downloading)
      SyntheticReplyAttachableState.Decoding -> stringResource(id = R.string.reply_synthetic_file_state_decoding_preview)
      SyntheticReplyAttachableState.Done -> stringResource(id = R.string.reply_synthetic_file_state_done)
    }

    val textColor = if (ThemeEngine.isDarkColor(chanTheme.backColorCompose)) {
      Color.White
    } else {
      Color.Black
    }

    KurobaComposeText(
      modifier = Modifier
        .wrapContentSize()
        .align(Alignment.Center),
      text = text,
      color = textColor,
      fontSize = 14.ktu
    )
  }
}

@Composable
private fun AttachedMediaThumbnail(
  replyLayoutViewModel: ReplyLayoutViewModel,
  replyLayoutEnabled: Boolean,
  replyFileAttachable: ReplyFileAttachable,
  mediaWidth: Dp,
  mediaHeight: Dp,
  onAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachedMediaLongClicked: (ReplyFileAttachable) -> Unit,
  onRemoveAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachableSelectionChanged: (ReplyFileAttachable, Boolean) -> Unit,
  onAttachableStatusIconButtonClicked: (ReplyFileAttachable) -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current

  val mediaHeightPx = with(density) { mediaHeight.roundToPx() }
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
      val transformations = if (replyFileAttachable.attachAdditionalInfo.maxAttachedFilesCountExceeded) {
        listOf(GrayscaleTransformation())
      } else {
        listOf()
      }

      when (val replyFileResult = replyLayoutViewModel.getReplyFileByUuid(replyFileAttachable.fileUuid)) {
        is ModularResult.Error -> {
          Logger.error("AttachedMediaThumbnail", replyFileResult.error) {
            "getReplyFileByUuid(${replyFileAttachable.fileUuid})"
          }

          value = ImageRequest.Builder(context)
            .data(null)
            .crossfade(true)
            .size(mediaHeightPx)
            .transformations(transformations)
            .build()
        }
        is ModularResult.Value -> {
          val previewFileOnDisk = replyFileResult.value.previewFileOnDisk
          if (previewFileOnDisk == null) {
            value = null
            return@produceState
          }

          value = ImageRequest.Builder(context)
            .data(previewFileOnDisk)
            .crossfade(true)
            .size(mediaHeightPx)
            .transformations(transformations)
            .build()
        }
      }
    }

    var imageStateMut by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val imageState = imageStateMut

    Box {
      if (imageRequest != null) {
        AsyncImage(
          modifier = Modifier
            .fillMaxSize()
            .kurobaClickable(
              enabled = replyLayoutEnabled,
              bounded = true,
              onClick = { onAttachedMediaClicked(replyFileAttachable) },
              onLongClick = { onAttachedMediaLongClicked(replyFileAttachable) }
            ),
          model = imageRequest,
          contentDescription = "Attached media",
          contentScale = ContentScale.Crop,
          alpha = alpha,
          onState = { state -> imageStateMut = state }
        )
      } else {
        Shimmer(modifier = Modifier.fillMaxSize())
      }

      if (imageState is AsyncImagePainter.State.Error) {
        Logger.error("AttachedMediaThumbnail") {
          "AttachedMediaThumbnail() attachedMediaFilePath: '${replyFileAttachable.fileUuid}', " +
            "error: '${imageState.result.throwable.errorMessageOrClassName()}'"
        }

        Column(
          modifier = Modifier.align(Alignment.Center)
        ) {
          KurobaComposeIcon(
            modifier = Modifier
              .size(24.dp)
              .align(Alignment.CenterHorizontally),
            drawableId = R.drawable.ic_baseline_warning_24
          )

          Spacer(modifier = Modifier.height(8.dp))

          KurobaComposeText(
            text = stringResource(id = R.string.reply_layout_failed_to_decode_media),
            color = Color.White,
            fontSize = 10.ktu
          )
        }
      }
    }

    val overlayBgColor = remember { Color.Black.copy(alpha = 0.4f) }

    if (replyFileAttachable.imageDimensions != null || replyFileAttachable.fileSize > 0) {
      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .alpha(alpha)
          .drawBehind { drawRect(color = overlayBgColor) }
          .padding(2.dp)
      ) {
        MediaDimensions(replyFileAttachable)

        MediaFileSize(replyFileAttachable)

        SpoilerMark(replyFileAttachable)
      }
    }

    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .alpha(alpha)
        .drawBehind { drawRect(color = overlayBgColor) }
        .padding(2.dp)
    ) {
      AttachableSelectionIndicatorButton(
        replyLayoutEnabled = replyLayoutEnabled,
        replyFileAttachable = replyFileAttachable,
        onAttachableSelectionChanged = onAttachableSelectionChanged
      )

      Spacer(modifier = Modifier.width(2.dp))

      AttachableStatusIconButton(
        replyLayoutEnabled = replyLayoutEnabled,
        replyFileAttachable = replyFileAttachable,
        onAttachableStatusIconButtonClicked = onAttachableStatusIconButtonClicked
      )

      Spacer(modifier = Modifier.width(2.dp))

      RemoveAttachableButton(
        replyLayoutEnabled = replyLayoutEnabled,
        onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
        replyFileAttachable = replyFileAttachable
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .alpha(alpha)
        .drawBehind { drawRect(color = overlayBgColor) }
        .padding(vertical = 2.dp),
      horizontalArrangement = Arrangement.Center
    ) {
      KurobaComposeMiddleEllipsisText(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight(),
        text = replyFileAttachable.fileName,
        color = Color.White,
        fontSize = 10.ktu.fixedSize(),
      )
    }
  }
}

@Composable
private fun SpoilerMark(replyFileAttachable: ReplyFileAttachable) {
  if (replyFileAttachable.spoilerInfo?.markedAsSpoiler == true) {
    KurobaComposeText(
      text = stringResource(id = R.string.layout_reply_files_area_spoiler_mark),
      color = remember { Color(0xFF10b1e6L) },
      fontSize = 10.ktu.fixedSize()
    )
  }
}

@Composable
private fun MediaFileSize(replyFileAttachable: ReplyFileAttachable) {
  if (replyFileAttachable.fileSize > 0) {
    val fileSizeString = remember(replyFileAttachable.fileSize) {
      return@remember ChanPostUtils.getReadableFileSize(replyFileAttachable.fileSize)
    }

    KurobaComposeText(
      text = fileSizeString,
      color = Color.White,
      fontSize = 10.ktu.fixedSize()
    )
  }
}

@Composable
private fun MediaDimensions(replyFileAttachable: ReplyFileAttachable) {
  if (replyFileAttachable.imageDimensions != null) {
    val dimensionsString = remember(replyFileAttachable.imageDimensions) {
      val imageDimensions = replyFileAttachable.imageDimensions
      return@remember "${imageDimensions.width}x${imageDimensions.height}"
    }

    KurobaComposeText(
      text = dimensionsString,
      color = Color.White,
      fontSize = 10.ktu.fixedSize()
    )
  }
}

@Composable
fun AttachableSelectionIndicatorButton(
  replyLayoutEnabled: Boolean,
  replyFileAttachable: ReplyFileAttachable,
  onAttachableSelectionChanged: (ReplyFileAttachable, Boolean) -> Unit
) {
  KurobaComposeSelectionIndicator(
    enabled = replyLayoutEnabled,
    padding = 4.dp,
    currentlySelected = replyFileAttachable.selected,
    onSelectionChanged = { selected -> onAttachableSelectionChanged(replyFileAttachable, selected) }
  )
}

@Composable
private fun AttachableStatusIconButton(
  replyLayoutEnabled: Boolean,
  replyFileAttachable: ReplyFileAttachable,
  onAttachableStatusIconButtonClicked: (ReplyFileAttachable) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val attachAdditionalInfo = replyFileAttachable.attachAdditionalInfo

  val iconTintWithDrawableId: Pair<IconTint, Int> = remember(attachAdditionalInfo, chanTheme) {
    val (color, drawableId) = when {
      attachAdditionalInfo.anyLimitsExceeded() -> chanTheme.colorError to R.drawable.ic_alert
      attachAdditionalInfo.hasGspExifData() -> chanTheme.colorWarning to R.drawable.ic_alert
      attachAdditionalInfo.hasOrientationExifData() -> chanTheme.colorWarning to R.drawable.ic_help_outline_white_24dp
      else -> chanTheme.colorInfo to R.drawable.ic_help_outline_white_24dp
    }

    return@remember IconTint.TintWithColor(color) to drawableId
  }

  val iconTint = iconTintWithDrawableId.first
  val drawableId = iconTintWithDrawableId.second

  KurobaComposeIcon(
    modifier = Modifier
      .kurobaClickable(
        enabled = replyLayoutEnabled,
        bounded = false,
        onClick = { onAttachableStatusIconButtonClicked(replyFileAttachable) }
      ),
    enabled = replyLayoutEnabled,
    drawableId = drawableId,
    iconTint = iconTint
  )
}

@Composable
private fun RemoveAttachableButton(
  replyLayoutEnabled: Boolean,
  onRemoveAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  replyFileAttachable: ReplyFileAttachable
) {
  val iconTint = remember {
    IconTint.TintWithColor(Color.White)
  }

  KurobaComposeIcon(
    modifier = Modifier
      .kurobaClickable(
        enabled = replyLayoutEnabled,
        bounded = false,
        onClick = { onRemoveAttachedMediaClicked(replyFileAttachable) }
      ),
    enabled = replyLayoutEnabled,
    drawableId = R.drawable.ic_baseline_clear_24,
    iconTint = iconTint
  )
}