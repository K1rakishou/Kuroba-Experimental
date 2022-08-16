/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

abstract class JsonReaderRequest<T>(
  protected val request: Request,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) {

  @OptIn(ExperimentalTime::class)
  open suspend fun execute(): JsonReaderResponse<T> {
    return withContext(Dispatchers.IO) {
      val response = Try {
        val timedValue = measureTimedValue {
          proxiedOkHttpClient.get().okHttpClient().suspendCall(request)
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