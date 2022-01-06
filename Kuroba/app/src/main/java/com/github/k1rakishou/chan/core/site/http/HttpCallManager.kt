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
package com.github.k1rakishou.chan.core.site.http

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Manages the [HttpCall] executions.
 */
@DoNotStrip
class HttpCallManager @Inject constructor(
  private val proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
  private val appConstants: AppConstants
) {
  
  /**
   * Use this one when you want to send a Post request and want to show some progress indicator
   * */
  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun <T : HttpCall> makePostHttpCallWithProgress(
    httpCall: T
  ): Flow<HttpCall.HttpCallWithProgressResult<T>> {
    return channelFlow {
      try {
        val requestBuilder = Request.Builder()

        try {
          httpCall.setup(requestBuilder) { fileIndex, totalFiles, percent ->
            val sendResult = trySend(HttpCall.HttpCallWithProgressResult.Progress(fileIndex, totalFiles, percent))
            if (sendResult.isSuccess) {
              return@setup
            }

            if (sendResult.isClosed) {
              throw CancellationException()
            }

            if (sendResult.isFailure) {
              val exception = sendResult.exceptionOrNull()
              if (exception != null) {
                throw exception
              }

              error("trySend failed for unknown reason")
            }
          }
        } catch (error: Throwable) {
          send(HttpCall.HttpCallWithProgressResult.Fail(httpCall, error))
          return@channelFlow
        }

        httpCall.site.requestModifier().modifyHttpCall(httpCall, requestBuilder)

        when (val httpCallResult = makeHttpCallInternal(requestBuilder, httpCall)) {
          is HttpCall.HttpCallResult.Success -> {
            send(HttpCall.HttpCallWithProgressResult.Success(httpCallResult.httpCall))
          }
          is HttpCall.HttpCallResult.Fail -> {
            send(HttpCall.HttpCallWithProgressResult.Fail(httpCallResult.httpCall, httpCallResult.error))
          }
        }
      } catch (error: Throwable) {
        send(HttpCall.HttpCallWithProgressResult.Fail(httpCall, error))
      }
    }
  }
  
  /**
   * Use this one for every other request
   * */
  suspend fun <T : HttpCall> makeHttpCall(httpCall: T): HttpCall.HttpCallResult<T> {
    val requestBuilder = Request.Builder()
    
    httpCall.setup(requestBuilder, null)
    httpCall.site.requestModifier().modifyHttpCall(httpCall, requestBuilder)
    
    return makeHttpCallInternal(requestBuilder, httpCall)
  }
  
  @OptIn(ExperimentalTime::class)
  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun <T : HttpCall> makeHttpCallInternal(
    requestBuilder: Request.Builder,
    httpCall: T
  ): HttpCall.HttpCallResult<T> {
    return withContext(Dispatchers.IO) {
      val request = requestBuilder
        .build()

      val (response, duration) = Try {
        return@Try measureTimedValue { proxiedOkHttpClient.get().okHttpClient().suspendCall(request) }
      }.safeUnwrap { error ->
        Logger.e(TAG, "Error while trying to execute request (${httpCall.javaClass.simpleName})", error)
        return@withContext HttpCall.HttpCallResult.Fail(httpCall, error)
      }

      Logger.d(TAG, "Request (${httpCall.javaClass.simpleName}, ${request.url}) execution success, took $duration")

      val body = response.body
        ?: return@withContext HttpCall.HttpCallResult.Fail(
          httpCall,
          IOException("Response body is null, status = ${response.code}")
        )

      return@withContext body.use { responseBody ->
        try {
          val responseString = responseBody.string()
          httpCall.process(response, responseString)
          return@use HttpCall.HttpCallResult.Success(httpCall)
        } catch (error: Throwable) {
          return@use HttpCall.HttpCallResult.Fail(httpCall, error)
        }
      }
    }
  }

  companion object {
    private const val TAG = "HttpCallManager"
  }
  
}