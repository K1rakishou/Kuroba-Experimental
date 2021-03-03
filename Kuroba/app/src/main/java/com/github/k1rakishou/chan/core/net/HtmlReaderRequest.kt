package com.github.k1rakishou.chan.core.net

import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

abstract class HtmlReaderRequest<T>(
  protected val request: Request,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {

  @Suppress("BlockingMethodInNonBlockingContext")
  @OptIn(ExperimentalTime::class)
  open suspend fun execute(): HtmlReaderResponse<T> {
    return withContext(Dispatchers.IO) {
      val response = Try {
        val timedValue = measureTimedValue {
          proxiedOkHttpClient.okHttpClient().suspendCall(request)
        }

        Logger.d(TAG, "Request \"${this@HtmlReaderRequest.javaClass.simpleName}\" to \"${request.url}\" " +
          "took ${timedValue.duration.inMilliseconds}ms")

        return@Try timedValue.value
      }.safeUnwrap { error ->
        Logger.e(TAG, "Network request error", error)
        return@withContext HtmlReaderResponse.UnknownServerError(error)
      }

      if (!response.isSuccessful) {
        return@withContext HtmlReaderResponse.ServerError(response.code)
      }

      if (response.body == null) {
        return@withContext HtmlReaderResponse.UnknownServerError(IOException("Response has no body"))
      }

      try {
        return@withContext response.body!!.use { body ->
          return@use body.byteStream().use { inputStream ->
            val url = request.url.toString()

            val htmlDocument = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), url)
            return@use HtmlReaderResponse.Success(readHtml(url, htmlDocument))
          }
        }
      } catch (error: Throwable) {
        return@withContext HtmlReaderResponse.ParsingError(error)
      }
    }
  }

  protected abstract suspend fun readHtml(url: String, document: Document): T

  sealed class HtmlReaderResponse<out T> {
    class Success<out T>(val result: T) : HtmlReaderResponse<T>()
    class ServerError(val statusCode: Int) : HtmlReaderResponse<Nothing>()
    class ParsingError(val error: Throwable) : HtmlReaderResponse<Nothing>()

    class UnknownServerError(val error: Throwable) : HtmlReaderResponse<Nothing>() {
      fun isCloudFlareException(): Boolean {
        return error is CloudFlareHandlerInterceptor.CloudFlareDetectedException
      }
    }
  }

  companion object {
    private const val TAG = "HtmlReaderRequest"
  }
}