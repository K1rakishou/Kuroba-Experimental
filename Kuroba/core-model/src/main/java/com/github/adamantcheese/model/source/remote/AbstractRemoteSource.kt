package com.github.adamantcheese.model.source.remote

import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.util.ensureBackgroundThread
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class AbstractRemoteSource(
        protected val okHttpClient: OkHttpClient,
        protected val logger: Logger
) {

    protected suspend fun OkHttpClient.suspendCall(request: Request): Response {
        return suspendCancellableCoroutine { continuation ->
            ensureBackgroundThread()
            val call = newCall(request)

            continuation.invokeOnCancellation {
                safeRun { call.cancel() }.ignore()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        val exception = IOException("Bad status code: ${response.code}")
                        continuation.resumeWithException(exception)
                    } else {
                        continuation.resume(response)
                    }
                }
            })
        }
    }

}