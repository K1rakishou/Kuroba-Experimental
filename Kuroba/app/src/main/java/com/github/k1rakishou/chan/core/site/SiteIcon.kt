package com.github.k1rakishou.chan.core.site

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toBitmap
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl

class SiteIcon private constructor(
  private val imageLoaderDeprecated: Lazy<ImageLoaderDeprecated>
) {
  var url: HttpUrl? = null
  var drawable: BitmapDrawable? = null

  private var requestDisposable: ImageLoaderDeprecated.ImageLoaderRequestDisposable? = null

  fun cancel() {
    requestDisposable?.dispose()
    requestDisposable = null
  }

  suspend fun getIconSuspend(context: Context): BitmapDrawable {
    return suspendCancellableCoroutine { cancellableContinuation ->
      getIcon(
        context = context,
        resultFunc = { bitmapDrawable -> cancellableContinuation.resumeValueSafe(bitmapDrawable) },
        errorDrawableId = R.drawable.error_icon,
        errorFunc = { bitmapDrawable -> cancellableContinuation.resumeValueSafe(bitmapDrawable) }
      )

      cancellableContinuation.invokeOnCancellation { error ->
        if (error != null) {
          cancel()
        }
      }
    }
  }

  fun getIcon(
    context: Context,
    resultFunc: (BitmapDrawable) -> Unit,
    errorDrawableId: Int? = null,
    errorFunc: ((BitmapDrawable) -> Unit)? = null
  ) {
    if (drawable != null) {
      resultFunc(drawable!!)
      return
    }

    if (url == null) {
      return
    }

    cancel()

    requestDisposable = imageLoaderDeprecated.get().loadFromNetwork(
      context = context,
      requestUrl = url.toString(),
      cacheFileType = CacheFileType.SiteIcon,
      imageSize = ImageLoaderDeprecated.ImageSize.FixedImageSize(
        FAVICON_SIZE,
        FAVICON_SIZE,
      ),
      transformations = emptyList(),
      listener = object : ImageLoaderDeprecated.FailureAwareImageListener {
        override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
          this@SiteIcon.drawable = drawable
          resultFunc(drawable)
        }

        override fun onNotFound() {
          Logger.e(TAG, "onNotFound() url='$url'")

          if (errorFunc == null) {
            return
          }

          showErrorDrawable()
        }

        override fun onResponseError(error: Throwable) {
          Logger.e(TAG, "onResponseError() url='$url', error=${error.errorMessageOrClassName()}")

          if (errorFunc == null) {
            return
          }

          requireNotNull(errorDrawableId) { "errorDrawableId is null!" }
          showErrorDrawable()
        }

        private fun showErrorDrawable() {
          val drawable = AppModuleAndroidUtils.getDrawable(errorDrawableId!!)

          val errorDrawable = BitmapDrawable(
            AppModuleAndroidUtils.res,
            drawable.toBitmap()
          )

          errorFunc!!.invoke(errorDrawable)
        }
      })
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SiteIcon

    if (url != other.url) return false
    if (drawable != other.drawable) return false

    return true
  }

  override fun hashCode(): Int {
    var result = url?.hashCode() ?: 0
    result = 31 * result + (drawable?.hashCode() ?: 0)
    return result
  }

  companion object {
    private const val TAG = "SiteIcon"
    const val FAVICON_SIZE = 64

    @JvmStatic
    fun fromFavicon(imageLoaderDeprecated: Lazy<ImageLoaderDeprecated>, url: HttpUrl): SiteIcon {
      val siteIcon = SiteIcon(imageLoaderDeprecated)
      siteIcon.url = url
      return siteIcon
    }

    fun fromDrawable(imageLoaderDeprecated: Lazy<ImageLoaderDeprecated>, @DrawableRes drawableId: Int): SiteIcon {
      val siteIcon = SiteIcon(imageLoaderDeprecated)
      val drawable = AppModuleAndroidUtils.getDrawable(drawableId)

      siteIcon.drawable = BitmapDrawable(
        AppModuleAndroidUtils.res,
        drawable.toBitmap()
      )

      return siteIcon
    }
  }
}