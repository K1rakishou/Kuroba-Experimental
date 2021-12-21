package com.github.k1rakishou.chan.core.site.loader

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.JsonParseException
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import okhttp3.HttpUrl
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException


open class ChanLoaderException(
  private val exception: Throwable
) : Exception(exception) {

  val isNotFound: Boolean
    get() = exception is BadStatusResponseException && exception.isNotFoundError()

  val errorMessage: String
    get() {
      return when (exception) {
        is SocketTimeoutException,
        is SocketException,
        is UnknownHostException -> getString(R.string.thread_load_failed_network)
        is BadStatusResponseException -> {
          when {
            exception.isAuthError() -> getString(R.string.thread_load_failed_auth_error)
            exception.isForbiddenError() -> getString(R.string.thread_load_failed_forbidden_error)
            exception.isNotFoundError() -> getString(R.string.thread_load_failed_not_found)
            else -> getString(R.string.thread_load_failed_server, exception.status)
          }
        }
        is SSLException -> {
          if (exception.message != null) {
            val message = exception.message!!
            getString(R.string.thread_load_failed_ssl_with_reason, message)
          } else {
            getString(R.string.thread_load_failed_ssl)
          }
        }
        is JsonDataException,
        is JsonEncodingException,
        is JsonParseException -> getString(R.string.thread_load_failed_json_parsing)
        is CloudFlareHandlerInterceptor.CloudFlareDetectedException -> {
          getString(R.string.thread_load_failed_cloud_flare_detected)
        }
        is SiteError -> exception.shortMessage()
        is ClientException -> exception.errorMessageOrClassName()
        is ChanLoaderException -> exception.errorMessage
        else -> exception.message ?: getString(R.string.thread_load_failed_parsing)
      }
    }

  fun isRecoverableError(error: Throwable = exception): Boolean {
    return when (error) {
      is SocketTimeoutException,
      is SocketException,
      is UnknownHostException,
      is SSLException,
      is CloudFlareHandlerInterceptor.CloudFlareDetectedException -> true
      is ChanLoaderException -> isRecoverableError(error.exception)
      else -> false
    }
  }

  fun isCloudFlareError(): Boolean =
    exception is CloudFlareHandlerInterceptor.CloudFlareDetectedException

  fun getOriginalRequestHost(): String {
    if (!isCloudFlareError()) {
      throw IllegalStateException("Not a CloudFlareDetectedException error!")
    }

    val fullUrl = (exception as CloudFlareHandlerInterceptor.CloudFlareDetectedException).requestUrl

    return HttpUrl.Builder()
      .scheme("https")
      .host(fullUrl.host)
      .build()
      .toString()
  }

  fun isCoroutineCancellationError(): Boolean {
    return exception is kotlinx.coroutines.CancellationException
  }

  fun isCacheEmptyException(): Boolean = exception is CacheIsEmptyException

  companion object {
    fun cacheIsEmptyException(chanDescriptor: ChanDescriptor): ChanLoaderException {
      return ChanLoaderException(CacheIsEmptyException(chanDescriptor))
    }
  }

}

class ClientException(message: String) : ChanLoaderException(Exception(message))

class SiteError(
  val errorCode: Int,
  val errorMessage: String?
) : Exception("Site error.\nErrorCode: '${errorCode}'.\nErrorMessage: '${errorMessage}'") {

  fun shortMessage(): String {
    if (errorMessage != null) {
      return errorMessage
    }

    return "Error code: '$errorCode'"
  }

}

class CacheIsEmptyException(chanDescriptor: ChanDescriptor) : Exception("Cache is empty for /$chanDescriptor/")