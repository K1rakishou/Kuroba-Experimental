package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.CaptchaImageCache
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CaptchaDonation(
    private val appScope: CoroutineScope,
    private val captchaImageCache: CaptchaImageCache,
    private val proxiedOkHttpClient: RealProxiedOkHttpClient
) {
    @OptIn(ObsoleteCoroutinesApi::class)
    private val actor = appScope.actor<Data>(
        context = Dispatchers.IO + SupervisorJob(),
        capacity = Channel.UNLIMITED
    ) {
        for (data in this) {
            try {
                sendCaptcha(data.captchaBytes, data.captchaSolution)
            } catch (error: Throwable) {
                Logger.e(TAG, "sendCaptcha() error: ${error.errorMessageOrClassName()}")
            }
        }
    }

    fun donateCaptcha(
        chanDescriptor: ChanDescriptor,
        captchaSolution: CaptchaSolution.ChallengeWithSolution
    ) {
        val captchaBytes = captchaImageCache.consume(captchaSolution.uuid, chanDescriptor)
        if (captchaBytes == null || captchaBytes.isEmpty()) {
            return
        }

        val data = Data(
            captchaBytes,
            captchaSolution
        )

        if (actor.trySend(data).isSuccess) {
            Logger.d(TAG, "donateCaptcha() actor.trySend() success")
        } else {
            Logger.e(TAG, "donateCaptcha() actor.trySend() failed")
        }
    }

    private suspend fun sendCaptcha(
        captchaBytes: ByteArray,
        captchaSolution: CaptchaSolution.ChallengeWithSolution
    ) {
        Logger.d(TAG, "sendCaptcha() captchaBytes: ${captchaBytes.size}, captchaSolution: ${captchaSolution}")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "text",
                value = captchaSolution.solution.uppercase()
            )
            .addFormDataPart(
                name = "image",
                filename = "image.png",
                body = captchaBytes.toRequestBody(contentType = "image/png".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://captcha.chance.surf/kuroba.php")
            .post(requestBody)
            .build()

        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
        if (!response.isSuccessful) {
            val body = response.body?.string()?.take(256)
            Logger.e(TAG, "sendCaptcha() bad status code: ${response.code}, errorBody: '${body}'")
            return
        }

        Logger.d(TAG, "sendCaptcha() success")
    }

    private class Data(
        val captchaBytes: ByteArray,
        val captchaSolution: CaptchaSolution.ChallengeWithSolution
    )

    companion object {
        private const val TAG = "CaptchaDonation"
    }

}