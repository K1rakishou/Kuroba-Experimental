package com.github.k1rakishou.chan.ui.compose.image

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import coil.network.HttpException
import coil.size.Scale
import coil.transform.Transformation
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.utils.activityDependencies
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.ExceptionWithShortErrorMessage
import okhttp3.HttpUrl
import javax.net.ssl.SSLException

@Composable
fun KurobaComposeImage(
  modifier: Modifier,
  request: ImageLoaderRequest,
  controllerKey: ControllerKey?,
  contentScale: ContentScale,
  loading: (@Composable BoxScope.() -> Unit)? = null,
  error: (@Composable BoxScope.(Throwable) -> Unit)? = { throwable -> DefaultErrorHandler(throwable) }
) {
  var size by remember { mutableStateOf<IntSize>(IntSize.Zero) }

  Box(
    modifier = modifier.then(
      Modifier
        .onSizeChanged { newSize -> size = newSize }
    )
  ) {
    BuildInnerImage(
      controllerKey = controllerKey,
      size = size,
      request = request,
      contentScale = contentScale,
      loading = loading,
      error = error
    )
  }
}

@Composable
private fun DefaultErrorHandler(throwable: Throwable) {
  val errorMsg = when (throwable) {
    is ExceptionWithShortErrorMessage -> throwable.shortErrorMessage()
    is SSLException -> stringResource(id = R.string.ssl_error)
    is HttpException -> stringResource(id = R.string.http_error, throwable.response.code)
    else -> throwable::class.java.simpleName
  }

  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current
  val textColor = chanTheme.textColorSecondaryCompose

  var contentSize by remember { mutableStateOf<IntSize>(IntSize.Zero) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { intSize -> contentSize = intSize },
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (contentSize.width > 0 && contentSize.height > 0) {
      val displayText = with(density) {
        contentSize.width > 64.dp.roundToPx() && contentSize.height > 64.dp.roundToPx()
      }

      val iconSize = with(density) {
        val multiplier = if (displayText) 0.5f else 0.75f

        (minOf(contentSize.width, contentSize.height) * multiplier)
          .toDp()
          .coerceAtMost(64.dp)
      }

      if (iconSize > 16.dp) {
        KurobaComposeIcon(
          modifier = Modifier.size(iconSize),
          drawableId = R.drawable.ic_baseline_warning_24
        )

        if (displayText) {
          Spacer(modifier = Modifier.height(8.dp))

          KurobaComposeText(
            modifier = Modifier.wrapContentSize(),
            text = errorMsg,
            color = textColor,
            fontSize = 10.ktu.fixedSize(),
            maxLines = 3,
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}

@Composable
private fun BuildInnerImage(
  controllerKey: ControllerKey?,
  size: IntSize,
  request: ImageLoaderRequest,
  contentScale: ContentScale,
  loading: (@Composable BoxScope.() -> Unit)?,
  error: (@Composable BoxScope.(Throwable) -> Unit)?
) {
  val context = LocalContext.current
  val kurobaImageLoader = appDependencies().kurobaImageLoader
  val globalUiStateHolder = appDependencies().globalUiStateHolder
  val applicationVisibility = activityDependencies().applicationVisibilityManager

  val imageLoaderResult by produceState<ImageLoaderResult>(
    initialValue = ImageLoaderResult.NotInitialized,
    key1 = request,
    key2 = size,
    producer = {
      loadImage(
        kurobaImageLoader = kurobaImageLoader,
        applicationVisibilityManager = applicationVisibility,
        globalUiStateHolder = globalUiStateHolder,
        context = context,
        controllerKey = controllerKey,
        size = size,
        scale = Scale.FIT,
        request = request
      )
    }
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
      Image(
        modifier = Modifier.fillMaxSize(),
        painter = result.painter,
        contentDescription = null,
        contentScale = contentScale
      )
    }
  }
}

@Immutable
data class ImageLoaderRequest(
  val data: ImageLoaderRequestData,
  val transformations: List<Transformation> = emptyList<Transformation>(),
) {
  override fun toString(): String {
    return "ImageLoaderRequest(data=$data, transformations=${transformations.size})"
  }
}

@Immutable
sealed class ImageLoaderRequestData {
  fun uniqueKey(): String {
    return when (this) {
      is DrawableResource -> drawableId.toString()
      is File -> absolutePath
      is Uri -> uri.toString()
      is Url -> httpUrl.toString()
    }
  }

  fun asUrlOrNull(): HttpUrl? {
    if (this is Url) {
      return httpUrl
    }

    return null
  }

  data class File(val file: java.io.File) : ImageLoaderRequestData() {
    val absolutePath: String = file.absolutePath

    override fun toString(): String {
      return "File(file=${absolutePath})"
    }
  }

  data class Uri(
    val uri: android.net.Uri
  ) : ImageLoaderRequestData() {
    val uriString: String = uri.toString()
  }

  data class Url(
    val httpUrl: HttpUrl,
    val cacheFileType: CacheFileType,
    val postDescriptor: PostDescriptor? = null
  ) : ImageLoaderRequestData() {
    val httpUrlString: String = httpUrl.toString()
  }

  data class DrawableResource(
    @DrawableRes val drawableId: Int
  ) : ImageLoaderRequestData()
}
