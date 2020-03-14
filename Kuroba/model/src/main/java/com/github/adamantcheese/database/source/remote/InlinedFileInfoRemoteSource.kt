package com.github.adamantcheese.database.source.remote

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.InlinedFileInfo
import com.github.adamantcheese.database.util.errorMessageOrClassName
import okhttp3.OkHttpClient
import okhttp3.Request

class InlinedFileInfoRemoteSource(
        okHttpClient: OkHttpClient,
        loggerTag: String,
        logger: Logger
) : AbstractRemoteSource(okHttpClient, logger) {
    private val TAG = "$loggerTag InlinedFileInfoRemoteSource"

    suspend fun fetchFromNetwork(fileUrl: String): ModularResult<InlinedFileInfo> {
        logger.log(TAG, "fetchFromNetwork($fileUrl)")

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

            return@safeRun when (result) {
                is ModularResult.Value -> result.value
                is ModularResult.Error -> {
                    logger.logError(TAG, "Error while trying to extract inlined file info " +
                            "(${result.error.errorMessageOrClassName()})")

                    result.unwrap()
                }
            }
        }
    }
}