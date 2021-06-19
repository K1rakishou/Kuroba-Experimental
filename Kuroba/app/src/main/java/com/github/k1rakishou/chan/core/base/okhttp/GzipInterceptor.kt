package com.github.k1rakishou.chan.core.base.okhttp

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.GzipSource
import okio.buffer


class GzipInterceptor : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val response: Response = chain.proceed(chain.request())

    if (isGzipped(response)) {
      return unzip(response)
    }

    return response
  }

  private fun unzip(response: Response): Response {
    val responseBody = response.body
      ?: return response

    val gzipSource = GzipSource(responseBody.source())
    val resultBody = gzipSource.buffer().asResponseBody(responseBody.contentType())

    val strippedHeaders = response.headers.newBuilder()
      .removeAll("Content-Encoding")
      .removeAll("Content-Length")
      .build()

    return response.newBuilder()
      .headers(strippedHeaders)
      .body(resultBody)
      .message(response.message)
      .build()
  }

  private fun isGzipped(response: Response): Boolean {
    return response.header("Content-Encoding") != null
      && response.header("Content-Encoding").equals("gzip")
  }

}