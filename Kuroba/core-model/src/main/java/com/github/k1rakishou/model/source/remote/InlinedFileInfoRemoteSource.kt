package com.github.k1rakishou.model.source.remote

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.InlinedFileInfo
import com.github.k1rakishou.model.util.ensureBackgroundThread
import okhttp3.OkHttpClient
import okhttp3.Request

open class InlinedFileInfoRemoteSource(
  okHttpClient: OkHttpClient,
  loggerTag: String,
  logger: Logger
) : AbstractRemoteSource(okHttpClient, logger) {
  private val TAG = "$loggerTag InlinedFileInfoRemoteSource"

  open suspend fun fetchFromNetwork(fileUrl: String): ModularResult<InlinedFileInfo> {
    logger.log(TAG, "fetchFromNetwork($fileUrl)")
    ensureBackgroundThread()

    return Try {
      val httpRequest = Request.Builder()
        .url(fileUrl)
        .head()
        .build()

      val response = okHttpClient.suspendCall(httpRequest)
      if (!response.isSuccessful) {
        return@Try InlinedFileInfo.empty(fileUrl)
      }

      val result = InlinedFileInfoRemoteSourceHelper.extractInlinedFileInfo(
        fileUrl,
        response.headers
      )

      return@Try result.unwrap()
    }
  }
}