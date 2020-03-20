package com.github.adamantcheese.database.source.remote

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.data.InlinedFileInfo
import com.github.adamantcheese.database.util.ensureBackgroundThread
import okhttp3.Headers

object InlinedFileInfoRemoteSourceHelper {
    const val CONTENT_LENGTH_HEADER = "Content-Length"

    fun extractInlinedFileInfo(fileUrl: String, headers: Headers): ModularResult<InlinedFileInfo> {
        ensureBackgroundThread()

        return safeRun {
            val fileSize = headers[CONTENT_LENGTH_HEADER]?.toLong()

            return@safeRun InlinedFileInfo(fileUrl, fileSize)
        }
    }

}