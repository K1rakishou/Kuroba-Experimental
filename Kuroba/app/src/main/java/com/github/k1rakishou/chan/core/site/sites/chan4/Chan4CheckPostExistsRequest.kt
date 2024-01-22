package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.useHtmlReader
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy
import kotlinx.coroutines.delay
import okhttp3.Request

class Chan4CheckPostExistsRequest(
    private val proxiedOkHttpClientLazy: Lazy<RealProxiedOkHttpClient>,
    private val chan4: Chan4,
    private val postDescriptor: PostDescriptor
) {
    private val proxiedOkHttpClient: RealProxiedOkHttpClient
        get() = proxiedOkHttpClientLazy.get()

    suspend fun execute(): ModularResult<Boolean> {
        // Wait 1 second so that we are 100% sure that the post is registered on the server
        delay(1000)

        return ModularResult.Try {
            Logger.d(TAG, "postDescriptor: ${postDescriptor}")

            val url = chan4.endpoints().threadHtml(postDescriptor.threadDescriptor())
            if (url == null) {
                throw ClientException("Site '${chan4.name()}' doesn't support 'threadHtml' endpoint")
            }

            val request = Request.Builder().url(url).get().build()

            val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
            if (!response.isSuccessful) {
                throw ClientException(
                    "Failed to fetch thread html for descriptor ${postDescriptor.threadDescriptor()}, " +
                            "statusCode: ${response.code}"
                )
            }

            val body = response.body
            if (body == null) {
                throw ClientException(
                    "Failed to fetch thread html for descriptor ${postDescriptor.threadDescriptor()}, " +
                            "response body is null"
                )
            }

            return@Try body.useHtmlReader(url.toString()) { document ->
                return@useHtmlReader document.select("div[class^=postContainer]").any { element ->
                    val pcValue = element.attr("id")

                    val matchResult = POST_ID_REGEX.find(pcValue)
                        ?: return@any false

                    return@any matchResult.value.toLongOrNull() == postDescriptor.postNo
                }
            }
        }
    }

    companion object {
        private const val TAG = "Chan4CheckPostExistsRequest"

        private val POST_ID_REGEX = "(\\d+)".toRegex()
    }

}