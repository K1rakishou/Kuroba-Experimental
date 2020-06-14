package com.github.adamantcheese.model.source.remote

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.util.ensureBackgroundThread
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