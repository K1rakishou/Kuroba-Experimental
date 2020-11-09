package com.github.k1rakishou.model.source.remote

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.InlinedFileInfo
import com.github.k1rakishou.model.util.ensureBackgroundThread
import okhttp3.OkHttpClient
import okhttp3.Request

open class InlinedFileInfoRemoteSource(
  okHttpClient: OkHttpClient
) : AbstractRemoteSource(okHttpClient) {
  private val TAG = "InlinedFileInfoRemoteSource"

  open suspend fun fetchFromNetwork(fileUrl: String): ModularResult<InlinedFileInfo> {
    Logger.d(TAG, "fetchFromNetwork($fileUrl)")
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