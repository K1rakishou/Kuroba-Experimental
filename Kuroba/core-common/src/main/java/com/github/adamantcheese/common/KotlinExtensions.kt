package com.github.adamantcheese.common

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun OkHttpClient.suspendCall(request: Request): Response {
    return suspendCancellableCoroutine { continuation ->
        val call = newCall(request)

        continuation.invokeOnCancellation {
            ModularResult.safeRun { call.cancel() }.ignore()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }
}