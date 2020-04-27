package com.github.adamantcheese.model.source.remote

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.common.suspendCall
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.util.ensureBackgroundThread
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

        return safeRun {
            val httpRequest = Request.Builder()
                    .url(fileUrl)
                    .head()
                    .build()

            val response = okHttpClient.suspendCall(httpRequest)
            if (!response.isSuccessful) {
                return@safeRun InlinedFileInfo.empty(fileUrl)
            }

            val result = InlinedFileInfoRemoteSourceHelper.extractInlinedFileInfo(
                    fileUrl,
                    response.headers
            )

            return@safeRun result.unwrap()
        }
    }
}