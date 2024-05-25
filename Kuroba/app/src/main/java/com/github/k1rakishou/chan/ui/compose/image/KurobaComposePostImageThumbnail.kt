package com.github.k1rakishou.chan.ui.compose.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.network.HttpException
import coil.size.Scale
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.Shimmer
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.utils.KurobaMediaType
import com.github.k1rakishou.chan.utils.activityDependencies
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.ExceptionWithShortErrorMessage
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl
import javax.net.ssl.SSLException

@Immutable
interface PostImageThumbnailKey {
  val postDescriptor: PostDescriptor
  val thumbnailImageUrl: HttpUrl?
  val fullImageUrl: HttpUrl?
}

@Stable
class ImageLoaderRequestProvider(
  val key: FullKey,
  val provide: suspend () -> ImageLoaderRequest?
) {

  @Immutable
  class FullKey(
    val keys: Array<Any?>
  ) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as FullKey

      return keys.contentEquals(other.keys)
    }

    override fun hashCode(): Int {
      return keys.contentHashCode()
    }
  }

}

@Composable
fun KurobaComposePostImageThumbnail(
  modifier: Modifier,
  controllerKey: ControllerKey?,
  postImageThumbnailKey: PostImageThumbnailKey,
  requestProvider: ImageLoaderRequestProvider,
  mediaType: KurobaMediaType,
  backgroundColor: Color = LocalChanTheme.current.backColorSecondaryCompose,
  hasAudio: Boolean = false,
  isNsfwModeEnabled: Boolean = false,
  displayErrorMessage: Boolean = true,
  showShimmerEffectWhenLoading: Boolean = true,
  contentDescription: String? = null,
  onClick: (PostImageThumbnailKey) -> Unit,
  onLongClick: (PostImageThumbnailKey) -> Unit
) {
  val context = LocalContext.current
  val kurobaImageLoader = appDependencies().kurobaImageLoader
  val globalUiStateHolder = appDependencies().globalUiStateHolder
  val applicationVisibilityManager = activityDependencies().applicationVisibilityManager

  var size by remember { mutableStateOf<IntSize>(IntSize.Zero) }

  Box(
    modifier = Modifier
      .kurobaClickable(
        bounded = true,
        onLongClick = { onLongClick(postImageThumbnailKey) },
        onClick = { onClick(postImageThumbnailKey) }
      )
      .drawBehind { drawRect(color = backgroundColor) }
      .onSizeChanged { intSize -> size = intSize }
      .then(modifier)
  ) {
    val imageLoaderResultMut by produceState<ImageLoaderResult>(
      initialValue = ImageLoaderResult.Loading,
      key1 = requestProvider.key,
      key2 = size,
      producer = {
        loadImage(
          kurobaImageLoader = kurobaImageLoader,
          applicationVisibilityManager = applicationVisibilityManager,
          globalUiStateHolder = globalUiStateHolder,
          context = context,
          controllerKey = controllerKey,
          size = size,
          scale = Scale.FIT,
          requestProvider = requestProvider
        )
      }
    )
    val imageLoaderResult = imageLoaderResultMut

    if (imageLoaderResult is ImageLoaderResult.Success) {
      Image(
        modifier = Modifier
          .fillMaxSize()
          .drawWithContent {
            drawContent()

            if (isNsfwModeEnabled) {
              drawRect(color = Color.DarkGray.copy(alpha = 0.9f))
            }
          },
        painter = imageLoaderResult.painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop
      )
    }

    PostImageThumbnailOverlay(
      modifier = Modifier.fillMaxSize(),
      hasAudio = hasAudio,
      displayErrorMessage = displayErrorMessage,
      showShimmerEffectWhenLoading = showShimmerEffectWhenLoading,
      mediaType = mediaType,
      backgroundColor = backgroundColor,
      imageLoaderResult = imageLoaderResult
    )
  }
}

@Composable
fun PostImageThumbnailOverlay(
  modifier: Modifier = Modifier,
  hasAudio: Boolean,
  displayErrorMessage: Boolean,
  showShimmerEffectWhenLoading: Boolean,
  mediaType: KurobaMediaType,
  backgroundColor: Color,
  imageLoaderResult: ImageLoaderResult
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    if (imageLoaderResult is ImageLoaderResult.Success) {
      if (mediaType is KurobaMediaType.Video || mediaType is KurobaMediaType.Gif) {
        Row {
          if (mediaType is KurobaMediaType.Video && hasAudio) {
            KurobaComposeIcon(
              drawableId = R.drawable.ic_volume_up_white_24dp,
              iconTint = IconTint.TintWithColor(Color.White)
            )
          } else {
            KurobaComposeIcon(
              drawableId = R.drawable.ic_play_circle_outline_white_24dp,
              iconTint = IconTint.TintWithColor(Color.White)
            )
          }
        }
      }
    } else if (imageLoaderResult is ImageLoaderResult.Error) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        PostImageThumbnailError(
          displayErrorMessage = displayErrorMessage,
          error = imageLoaderResult.throwable
        )
      }
    } else if (showShimmering(imageLoaderResult, showShimmerEffectWhenLoading)) {
      Shimmer(
        modifier = Modifier.fillMaxSize(),
        mainShimmerColor = backgroundColor
      )
    }
  }
}

@Composable
private fun PostImageThumbnailError(
  displayErrorMessage: Boolean,
  error: Throwable
) {
  val appResources = appDependencies().appResources

  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    KurobaComposeIcon(
      drawableId = R.drawable.ic_baseline_warning_24
    )

    var errorTextMut by remember { mutableStateOf<String?>(null) }
    val errorText = errorTextMut

    LaunchedEffect(error, displayErrorMessage) {
      if (!displayErrorMessage) {
        return@LaunchedEffect
      }

      errorTextMut = when (error) {
        is ExceptionWithShortErrorMessage -> error.shortErrorMessage()
        is SSLException -> appResources.string(R.string.ssl_error)
        is HttpException -> appResources.string(R.string.http_error, error.response.code)
        else -> error::class.java.simpleName
      }
    }

    if (errorText != null) {
      Spacer(modifier = Modifier.height(4.dp))

      KurobaComposeText(
        modifier = Modifier.fillMaxWidth(),
        text = errorText,
        fontSize = 11.ktu.fixedSize(),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun showShimmering(
  imageLoaderResult: ImageLoaderResult,
  showShimmerEffectWhenLoading: Boolean
): Boolean {
  return (imageLoaderResult is ImageLoaderResult.NotInitialized || imageLoaderResult is ImageLoaderResult.Loading)
    && showShimmerEffectWhenLoading
}
