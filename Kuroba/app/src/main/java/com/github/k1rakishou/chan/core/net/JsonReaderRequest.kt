package com.github.k1rakishou.chan.core.net

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.google.gson.stream.JsonReader
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.time.measureTimedValue

abstract class JsonReaderRequest<T>(
  protected val request: Request,
  private val proxiedOkHttpClient: RealProxiedOkHttpClient
) {

  open suspend fun execute(): JsonReaderResponse<T> {
    return withContext(Dispatchers.IO) {
      val response = Try {
        val timedValue = measureTimedValue {
          proxiedOkHttpClient.okHttpClient().suspendCall(request)
        }

        Logger.d(TAG, "Request \"${this@JsonReaderRequest.javaClass.simpleName}\" to \"${request.url}\" " +
          "took ${timedValue.duration}")

        return@Try timedValue.value
      }.safeUnwrap { error ->
        if (error.isExceptionImportant()) {
          Logger.e(TAG, "Network request error", error)
        } else {
          Logger.e(TAG, "Network request error: ${error.errorMessageOrClassName()}")
        }

        return@withContext JsonReaderResponse.UnknownServerError(error)
      }

      if (!response.isSuccessful) {
        return@withContext JsonReaderResponse.ServerError(response.code)
      }

      if (response.body == null) {
        return@withContext JsonReaderResponse.UnknownServerError(EmptyBodyResponseException())
      }

      try {
        return@withContext response.body!!.use { body ->
          return@use body.byteStream().use { inputStream ->
            return@use JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { jsonReader ->
              return@use JsonReaderResponse.Success(readJson(jsonReader))
            }
          }
        }
      } catch (error: Throwable) {
        return@withContext JsonReaderResponse.ParsingError(error)
      }
    }
  }

  protected abstract suspend fun readJson(reader: JsonReader): T

  sealed class JsonReaderResponse<out T> {
    class Success<out T>(val result: T) : JsonReaderResponse<T>()
    class ServerError(val statusCode: Int) : JsonReaderResponse<Nothing>()
    class UnknownServerError(val error: Throwable) : JsonReaderResponse<Nothing>()
    class ParsingError(val error: Throwable) : JsonReaderResponse<Nothing>()
  }

  companion object {
    private const val TAG = "JsonReaderRequest"
  }

}