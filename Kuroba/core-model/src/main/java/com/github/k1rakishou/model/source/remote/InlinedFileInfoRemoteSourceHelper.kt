package com.github.k1rakishou.model.source.remote

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.InlinedFileInfo
import com.github.k1rakishou.model.util.ensureBackgroundThread
import okhttp3.Headers

object InlinedFileInfoRemoteSourceHelper {
  const val CONTENT_LENGTH_HEADER = "Content-Length"

  fun extractInlinedFileInfo(fileUrl: String, headers: Headers): ModularResult<InlinedFileInfo> {
    ensureBackgroundThread()

    return Try {
      val fileSize = headers[CONTENT_LENGTH_HEADER]?.toLong()

      return@Try InlinedFileInfo(fileUrl, fileSize)
    }
  }

}