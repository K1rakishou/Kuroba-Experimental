package com.github.adamantcheese.common

import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun OkHttpClient.suspendCall(request: Request): Response {
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val call = newCall(request)

        continuation.invokeOnCancellation {
            ModularResult.safeRun { call.cancel() }.ignore()
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