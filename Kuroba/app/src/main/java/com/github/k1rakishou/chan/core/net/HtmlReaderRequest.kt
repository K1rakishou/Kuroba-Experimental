package com.github.k1rakishou.chan.core.net

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.suspendCall
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

abstract class HtmlReaderRequest<T>(
  protected val htmlRequestType: HtmlRequestType,
  protected val request: Request,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {

  @Suppress("BlockingMethodInNonBlockingContext")
  @OptIn(ExperimentalTime::class)
  open suspend fun execute(): HtmlReaderResponse<T> {
    val response = Try {
      val timedValue = measureTimedValue {
        proxiedOkHttpClient.proxiedClient.suspendCall(request)
      }

      Logger.d(TAG, "Request \"${htmlRequestType.requestTag}\" to \"${request.url}\" " +
        "took ${timedValue.duration.inMilliseconds}ms")

      return@Try timedValue.value
    }.safeUnwrap { error ->
      Logger.e(TAG, "Network request error", error)
      return HtmlReaderResponse.UnknownServerError(error)
    }

    if (!response.isSuccessful) {
      return HtmlReaderResponse.ServerError(response.code)
    }

    if (response.body == null) {
      return HtmlReaderResponse.UnknownServerError(IOException("Response has no body"))
    }

    try {
      return response.body!!.use { body ->
        return@use body.byteStream().use { inputStream ->
          val htmlDocument = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), request.url.toString())

          return@use HtmlReaderResponse.Success(readHtml(htmlDocument))
        }
      }
    } catch (error: Throwable) {
      return HtmlReaderResponse.ParsingError(error)
    }
  }

  protected abstract suspend fun readHtml(document: Document): T

  enum class HtmlRequestType(val requestTag: String) {
    Chan4SearchRequest("Chan4Search")
  }

  sealed class HtmlReaderResponse<out T> {
    class Success<out T>(val result: T) : HtmlReaderResponse<T>()
    class ServerError(val statusCode: Int) : HtmlReaderResponse<Nothing>()
    class UnknownServerError(val error: Throwable) : HtmlReaderResponse<Nothing>()
    class ParsingError(val error: Throwable) : HtmlReaderResponse<Nothing>()
  }

  companion object {
    private const val TAG = "HtmlReaderRequest"
  }
}