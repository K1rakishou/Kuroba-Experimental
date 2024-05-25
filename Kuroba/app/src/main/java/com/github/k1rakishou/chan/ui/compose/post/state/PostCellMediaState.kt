package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi
import com.github.k1rakishou.chan.ui.compose.image.PostImageThumbnailKey
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.KurobaMediaType
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import okhttp3.HttpUrl

@Stable
data class PostCellMediaState(
  val postDescriptorUi: PostDescriptorUi,
  val thumbnailImageUrl: HttpUrl?,
  val fullImageUrl: HttpUrl?,
  val spoilerImageUrl: HttpUrl?,
  val kurobaMediaType: KurobaMediaType,
  val mediaWidth: Int?,
  val mediaHeight: Int?,
  val mediaSize: Long?,
  val extension: String?,
  val mediaDeleted: Boolean
) {
  private val _initialized = mutableStateOf(false)

  private val _mediaExtensionState = mutableStateOf<AnnotatedString?>(null)
  val mediaExtensionState: State<AnnotatedString?>
    get() = _mediaExtensionState

  private val _mediaDimensionsState = mutableStateOf<AnnotatedString?>(null)
  val mediaDimensionsState: State<AnnotatedString?>
    get() = _mediaDimensionsState

  private val _mediaSizeState = mutableStateOf<AnnotatedString?>(null)
  val mediaSizeState: State<AnnotatedString?>
    get() = _mediaSizeState

  suspend fun calculate(
    chanTheme: ChanTheme,
    isPostHidden: Boolean,
    forced: Boolean,
  ) {
    BackgroundUtils.ensureBackgroundThread()

    if (_initialized.value && !forced) {
      return
    }

    val mediaExtensionState = calculateMediaExtension(chanTheme)
    val mediaDimensionsState = calculateMediaDimensions(chanTheme)
    val mediaSizeState = calculateMediaSize(chanTheme)

    Snapshot.withMutableSnapshot {
      _mediaExtensionState.value = mediaExtensionState
      _mediaDimensionsState.value = mediaDimensionsState
      _mediaSizeState.value = mediaSizeState
      _initialized.value = true
    }
  }

  private fun calculateMediaExtension(chanTheme: ChanTheme): AnnotatedString {
    if (extension.isNullOrBlank()) {
      return AnnotatedString("")
    }

    return buildAnnotatedString {
      withStyle(SpanStyle(color = chanTheme.textColorHintCompose)) {
        append(extension.uppercase())
      }
    }
  }

  private fun calculateMediaDimensions(chanTheme: ChanTheme): AnnotatedString {
    if (mediaWidth == null || mediaHeight == null) {
      return AnnotatedString("")
    }

    return buildAnnotatedString {
      withStyle(SpanStyle(color = chanTheme.textColorHintCompose)) {
        append(mediaWidth.toString())
        append("x")
        append(mediaHeight.toString())
      }
    }
  }

  private fun calculateMediaSize(chanTheme: ChanTheme): AnnotatedString {
    if (mediaSize == null) {
      return AnnotatedString("")
    }

    return buildAnnotatedString {
      withStyle(SpanStyle(color = chanTheme.textColorHintCompose)) {
        append(ChanPostUtils.getReadableFileSize(mediaSize))
      }
    }
  }

  val postCellMediaKey = PostCellMediaKey(
    postDescriptor = postDescriptorUi.postDescriptor,
    thumbnailImageUrl = thumbnailImageUrl,
    fullImageUrl = fullImageUrl
  )

  val postDescriptor: PostDescriptor
    get() = postDescriptorUi.postDescriptor

}

@Immutable
data class PostCellMediaKey(
  override val postDescriptor: PostDescriptor,
  override val thumbnailImageUrl: HttpUrl?,
  override val fullImageUrl: HttpUrl?
) : PostImageThumbnailKey