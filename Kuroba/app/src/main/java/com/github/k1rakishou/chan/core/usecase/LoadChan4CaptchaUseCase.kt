package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4CaptchaSettings
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ParsingException
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.substringSafe
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.GsonJsonSetting
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class LoadChan4CaptchaUseCase(
    private val moshi: Moshi,
    private val siteManager: SiteManager,
    private val proxiedOkHttpClient: ProxiedOkHttpClient
) {

    suspend fun await(
        chanDescriptor: ChanDescriptor,
        ticket: String?
    ): ModularResult<CaptchaResult> {
        return ModularResult.Try {
            val captchaResult = loadCaptcha(
                chanDescriptor = chanDescriptor,
                ticket = ticket,
                loadWebsiteCaptcha = true
            )

            updateCaptchaTicket(
                chanDescriptor = chanDescriptor,
                captchaResult = captchaResult
            )

            return@Try captchaResult
        }
    }

    private suspend fun loadCaptcha(
        chanDescriptor: ChanDescriptor,
        ticket: String?,
        loadWebsiteCaptcha: Boolean
    ): CaptchaResult {
        val boardCode = chanDescriptor.boardDescriptor().boardCode
        val urlRaw = formatCaptchaUrl(chanDescriptor, boardCode, ticket, loadWebsiteCaptcha)

        Logger.d(TAG, "loadCaptcha($chanDescriptor) requesting $urlRaw")

        val requestBuilder = Request.Builder()
            .url(urlRaw)
            .get()

        siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())?.let { chan4 ->
            chan4.requestModifier().modifyCaptchaGetRequest(chan4, requestBuilder)
        }

        val request = requestBuilder.build()
        val captchaInfoRawAdapter = moshi.adapter(CaptchaInfoRaw::class.java)

        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
        if (!response.isSuccessful) {
            throw BadStatusResponseException(response.code)
        }

        val captchaResponseHtml = response.body?.string()
        if (captchaResponseHtml == null) {
            throw EmptyBodyResponseException()
        }

        val captchaInfoRawString = try {
            extractCaptchaInfoRawJson(captchaResponseHtml)
        } catch (error: Throwable) {
            Logger.error(TAG) {
                "loadCaptcha($chanDescriptor) extractCaptchaInfoRawJson() error: ${error.errorMessageOrClassName()}"
            }

            captchaResponseHtml
                .chunked(256)
                .forEach { captchaChunk -> Logger.e(TAG, "'${captchaChunk}'") }

            if (loadWebsiteCaptcha) {
                Logger.debug(TAG) {
                    "extractCaptchaInfoRawJson() Failed. Retrying captcha load with loadWebsiteCaptcha set to false"
                }

                return loadCaptcha(
                    chanDescriptor = chanDescriptor,
                    ticket = ticket,
                    loadWebsiteCaptcha = false
                )
            }

            throw FailedToExtractCaptchaJsonFromHtml()
        }

        try {
            val captchaInfoRaw = withContext(Dispatchers.IO) { captchaInfoRawAdapter.fromJson(captchaInfoRawString) }
            if (captchaInfoRaw == null) {
                throw ParsingException("Failed to parse 4chan captcha json, captchaInfoRawString: ${captchaInfoRawString}")
            }

            return CaptchaResult(captchaInfoRaw, captchaInfoRawString)
        } catch (error: Throwable) {
            Logger.error(TAG) {
                "loadCaptcha($chanDescriptor) captchaInfoRawAdapter.fromJson() error: ${error.errorMessageOrClassName()}"
            }

            captchaInfoRawString
                .chunked(256)
                .forEach { captchaChunk -> Logger.e(TAG, "'${captchaChunk}'") }

            if (loadWebsiteCaptcha) {
                Logger.debug(TAG) {
                    "captchaInfoRawAdapter.fromJson() Failed. Retrying captcha load with loadWebsiteCaptcha set to false"
                }

                return loadCaptcha(
                    chanDescriptor = chanDescriptor,
                    ticket = ticket,
                    loadWebsiteCaptcha = false
                )
            }

            throw FailedToExtractCaptchaJsonFromHtml()
        }
    }

    private fun updateCaptchaTicket(
        chanDescriptor: ChanDescriptor,
        captchaResult: CaptchaResult
    ) {
        val chan4CaptchaSettingsSetting = siteManager.bySiteDescriptor(Chan4.SITE_DESCRIPTOR)
            ?.getSettingBySettingId<GsonJsonSetting<Chan4CaptchaSettings>>(SiteSetting.SiteSettingId.Chan4CaptchaSettings)
            ?: return

        val newTicket = captchaResult.captchaInfoRaw.ticketAsString
        if (newTicket.isNullOrBlank()) {
            Logger.debug(TAG) { "updateCaptchaTicket($chanDescriptor) ticked is null or blank" }
            return
        }

        val oldTicket = chan4CaptchaSettingsSetting.get().captchaTicket

        Logger.debug(TAG) {
            "updateCaptchaTicket($chanDescriptor) " +
                    "updating currentTicket with '${StringUtils.formatToken(newTicket)}'"
        }

        chan4CaptchaSettingsSetting.update(sync = true) { chan4CaptchaSettings ->
            chan4CaptchaSettings.copy(
                captchaTicket = newTicket,
                lastRefreshTime = System.currentTimeMillis()
            )
        }

        Logger.debug(TAG) {
            "updateCaptchaTicket() Successfully refreshed 4chan captcha ticket! " +
                    "Old: ${StringUtils.formatToken(oldTicket)}, " +
                    "New: ${StringUtils.formatToken(newTicket)}"
        }
    }

    private fun formatCaptchaUrl(
        chanDescriptor: ChanDescriptor,
        boardCode: String,
        ticket: String?,
        loadWebsiteCaptcha: Boolean
    ): String {
        return buildString {
            when (chanDescriptor) {
                is ChanDescriptor.CompositeCatalogDescriptor -> {
                    error("Cannot use CompositeCatalogDescriptor here")
                }
                is ChanDescriptor.CatalogDescriptor -> {
                    append("https://sys.4chan.org/captcha")

                    if (loadWebsiteCaptcha) {
                        append("?framed=1&board=${boardCode}")
                    } else {
                        append("?board=${boardCode}")
                    }

                    if (ticket.isNotNullNorEmpty()) {
                        append("&ticket=${ticket}")
                    }
                }
                is ChanDescriptor.ThreadDescriptor -> {
                    append("https://sys.4chan.org/captcha")

                    if (loadWebsiteCaptcha) {
                        append("?framed=1&board=${boardCode}&thread_id=${chanDescriptor.threadNo}")
                    } else {
                        append("?board=${boardCode}&thread_id=${chanDescriptor.threadNo}")
                    }

                    if (ticket.isNotNullNorEmpty()) {
                        append("&ticket=${ticket}")
                    }
                }
            }
        }
    }

    private fun extractCaptchaInfoRawJson(captchaResponseHtml: String): String {
        val postMessageFunc = ".postMessage("

        val jsonStart = captchaResponseHtml.indexOf(postMessageFunc)
            .takeIf { index -> index >= 0 }
            ?.plus(postMessageFunc.length)
            ?: -1

        if (jsonStart < 0) {
            throw ParsingException("Failed to find '${postMessageFunc}' in website captcha response")
        }

        if (captchaResponseHtml.getOrNull(jsonStart) != '{') {
            throw ParsingException("Failed to find json start ('{' symbol) in website captcha response")
        }

        val jsonEnd = StringUtils.findJsonEnd(captchaResponseHtml, jsonStart)
        if (jsonEnd == null || jsonEnd < 0) {
            throw ParsingException("Failed to find json end ('}' symbol) in website captcha response")
        }

        val json = captchaResponseHtml.substringSafe(jsonStart, jsonEnd)
        if (json.isNullOrBlank()) {
            throw ParsingException("Failed to extract json. jsonStart: ${jsonStart}, jsonEnd: ${jsonEnd}")
        }

        return json
            .removePrefix("{\"twister\":")
            .removeSuffix("}")
    }

    data class CaptchaResult(
        val captchaInfoRaw: CaptchaInfoRaw,
        val captchaInfoRawString: String
    )

    @JsonClass(generateAdapter = true)
    data class CaptchaInfoRaw(
        @Json(name = "error")
        val err: String?,
        @Json(name = "pcd_msg")
        val pcdMsg: String?,
        @Json(name = "cd")
        val cd: Int?,
        @Json(name = "pcd")
        val pcd: Int?,

        // For Slider captcha
        @Json(name = "bg")
        val bg: String?,
        @Json(name = "bg_width")
        val bgWidth: Int?,

        @Json(name = "cd_until")
        val cooldownUntil: Long?,
        @Json(name = "challenge")
        val challenge: String?,
        @Json(name = "img")
        val img: String?,
        @Json(name = "img_width")
        val imgWidth: Int?,
        @Json(name = "img_height")
        val imgHeight: Int?,
        @Json(name = "valid_until")
        val validUntil: Long?,
        @Json(name = "ttl")
        val ttl: Int?,
        @Json(name = "ticket")
        val ticket: Any?
    ) {
        val cooldown: Int?
            get() {
                if (pcd != null && pcd > 0) {
                    return pcd
                }

                if (cd != null && cd > 0) {
                    return cd
                }

                return null
            }

        val ticketAsString: String?
            get() = ticket as? String

        fun ttlMillis(): Int {
            return ttlSeconds() * 1000
        }

        fun ttlSeconds(): Int {
            return ttl ?: 120
        }

        fun isNoopChallenge(): Boolean {
            return challenge?.equals(Chan4CaptchaLayoutViewModel.NOOP_CHALLENGE, ignoreCase = true) == true
        }
    }

    class FailedToExtractCaptchaJsonFromHtml : ClientException(
        "Failed to extract 4chan captcha json from HTML. " +
                "This is most likely because the captcha format was changed."
    )

    companion object {
        private const val TAG = "LoadChan4CaptchaUseCase"
    }

}