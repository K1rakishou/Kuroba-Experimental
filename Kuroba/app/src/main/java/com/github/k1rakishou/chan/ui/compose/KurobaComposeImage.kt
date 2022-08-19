package com.github.k1rakishou.chan.ui.compose

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import coil.transform.Transformation
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.common.ExceptionWithShortErrorMessage
import com.github.k1rakishou.common.ModularResult
import okhttp3.HttpUrl
import javax.net.ssl.SSLException

@Suppress("UnnecessaryVariable")
@Composable
fun KurobaComposeImage(
  modifier: Modifier,
  request: ImageLoaderRequest,
  imageLoaderV2: ImageLoaderV2,
  contentScale: ContentScale = ContentScale.Fit,
  loading: (@Composable BoxScope.() -> Unit)? = null,
  error: (@Composable BoxScope.(Throwable) -> Unit)? = { throwable -> DefaultErrorHandler(throwable) },
  success: (@Composable () -> Unit)? = null
) {
  var size by remember { mutableStateOf<IntSize?>(null) }

  val measureModifier = Modifier.onSizeChanged { newSize ->
    if (newSize.width > 0 || newSize.height > 0) {
      size = newSize
    }
  }

  Box(modifier = modifier.then(measureModifier)) {
    BuildInnerImage(size, request, imageLoaderV2, contentScale, loading, error, success)
  }
}

@Composable
private fun BoxScope.DefaultErrorHandler(throwable: Throwable) {
  val errorMsg = when (throwable) {
    is ExceptionWithShortErrorMessage -> throwable.shortErrorMessage()
    is SSLException -> stringResource(id = R.string.ssl_error)
    else -> throwable::class.java.simpleName
  }

  val chanTheme = LocalChanTheme.current
  val textColor = chanTheme.textColorSecondaryCompose

  KurobaComposeText(
    modifier = Modifier.align(Alignment.Center),
    text = errorMsg,
    color = textColor,
    fontSize = 11.sp,
    maxLines = 3,
    textAlign = TextAlign.Center
  )
}

@Composable
private fun BuildInnerImage(
  size: IntSize?,
  request: ImageLoaderRequest,
  imageLoaderV2: ImageLoaderV2,
  contentScale: ContentScale,
  loading: (@Composable BoxScope.() -> Unit)? = null,
  error: (@Composable BoxScope.(Throwable) -> Unit)? = null,
  success: (@Composable () -> Unit)? = null
) {
  val context = LocalContext.current

  if (size == null) {
    return
  }

  val imageLoaderResult by produceState<ImageLoaderResult>(
    initialValue = ImageLoaderResult.NotInitialized,
    key1 = request,
    producer = { loadImage(context, request, size, imageLoaderV2) }
  )

  when (val result = imageLoaderResult) {
    ImageLoaderResult.NotInitialized -> {
      Spacer(modifier = Modifier.fillMaxSize())
      return
    }
    ImageLoaderResult.Loading -> {
      if (loading != null) {
        Box(modifier = Modifier.fillMaxSize()) {
          loading()
        }
      }

      return
    }
    is ImageLoaderResult.Error -> {
      if (error != null) {
        Box(modifier = Modifier.fillMaxSize()) {
          error(result.throwable)
        }
      }

      return
    }
    is ImageLoaderResult.Success -> {
      success?.invoke()

      Image(
        painter = result.painter,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

private suspend fun ProduceStateScope<ImageLoaderResult>.loadImage(
  context: Context,
  request: ImageLoaderRequest,
  size: IntSize,
  imageLoaderV2: ImageLoaderV2
) {
  this.value = ImageLoaderResult.Loading

  val result = when (val data = request.data) {
    is ImageLoaderRequestData.File,
    is ImageLoaderRequestData.Uri -> {
      val inputFile = if (data is ImageLoaderRequestData.File) {
        InputFile.JavaFile(data.file)
      } else {
        data as ImageLoaderRequestData.Uri
        InputFile.FileUri(context, data.uri)
      }

      imageLoaderV2.loadFromDiskSuspend(
        context = context,
        inputFile = inputFile,
        imageSize = ImageLoaderV2.ImageSize.FixedImageSize(size.width, size.height),
        transformations = request.transformations
      )
    }
    is ImageLoaderRequestData.Url -> {
      imageLoaderV2.loadFromNetworkSuspend(
        context = context,
        url = data.httpUrl.toString(),
        cacheFileType = data.cacheFileType,
        imageSize = ImageLoaderV2.ImageSize.FixedImageSize(size.width, size.height),
        transformations = request.transformations
      )
    }
    is ImageLoaderRequestData.DrawableResource -> {
      imageLoaderV2.loadFromResourcesSuspend(
        context = context,
        drawableId = data.drawableId,
        imageSize = ImageLoaderV2.ImageSize.FixedImageSize(size.width, size.height),
        transformations = request.transformations
      )
    }
  }.mapValue { bitmapDrawable -> BitmapPainter(bitmapDrawable.bitmap.asImageBitmap()) }

  this.value = when (result) {
    is ModularResult.Error -> ImageLoaderResult.Error(result.error)
    is ModularResult.Value -> ImageLoaderResult.Success(result.value)
  }
}

sealed class ImageLoaderResult {
  object NotInitialized : ImageLoaderResult()
  object Loading : ImageLoaderResult()
  data class Success(val painter: BitmapPainter) : ImageLoaderResult()
  data class Error(val throwable: Throwable) : ImageLoaderResult()
}

data class ImageLoaderRequest(
  val data: ImageLoaderRequestData,
  val transformations: List<Transformation> = emptyList<Transformation>(),
)

sealed class ImageLoaderRequestData {
  data class File(val file: java.io.File) : ImageLoaderRequestData()
  data class Uri(val uri: android.net.Uri) : ImageLoaderRequestData()
  data class Url(val httpUrl: HttpUrl, val cacheFileType: CacheFileType) : ImageLoaderRequestData()
  data class DrawableResource(@DrawableRes val drawableId: Int) : ImageLoaderRequestData()
}