package com.github.k1rakishou.chan.core.net

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.NotFoundException
import com.github.k1rakishou.common.suspendCall
import dagger.Lazy
import okhttp3.Request
import okhttp3.ResponseBody

abstract class AbstractRequest<T>(
  protected val request: Request,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
) {

  suspend fun execute(): ModularResult<T> {
    return ModularResult.Try {
      val response = proxiedOkHttpClient.get().okHttpClient().suspendCall(request)
      if (!response.isSuccessful) {
        if (response.code == 404) {
          throw NotFoundException()
        }

        throw BadStatusResponseException(response.code)
      }

      val body = response.body
        ?: throw EmptyBodyResponseException()

      return@Try processBody(body)
    }
  }

  abstract suspend fun processBody(responseBody: ResponseBody): T

}