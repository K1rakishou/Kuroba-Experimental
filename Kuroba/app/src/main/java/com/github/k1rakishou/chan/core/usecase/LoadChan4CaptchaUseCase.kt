package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4SiteSettings
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ParsingException
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.substringSafe
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.Request

class LoadChan4CaptchaUseCase(
  private val moshi: Moshi,
  private val siteManager: SiteManager,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {

  suspend fun await(
    chanDescriptor: ChanDescriptor,
    ticket: String?,
    mcl: String
  ): ModularResult<CaptchaResult> {
    return ModularResult.Try {
      val captchaResult = loadCaptcha(
        chanDescriptor = chanDescriptor,
        ticket = ticket,
        mcl = mcl
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
    mcl: String
  ): CaptchaResult {
    val boardCode = chanDescriptor.boardDescriptor().boardCode
    val urlRaw = formatCaptchaUrl(chanDescriptor, boardCode, ticket)

    Logger.d(TAG, "loadCaptcha(${chanDescriptor}, ${mcl.asFormattedToken()}) requesting $urlRaw")

    val requestBuilder = if (mcl.isBlank()) {
      Request.Builder()
        .url(urlRaw)
        .get()
    } else {
      with(MultipartBody.Builder()) {
        setType(MultipartBody.FORM)
        addFormDataPart("mcl", mcl)

        return@with Request.Builder()
          .url(urlRaw)
          .post(build())
      }
    }

    siteManager.bySiteDescriptorAndActive(chanDescriptor.siteDescriptor())?.let { chan4 ->
      chan4.requestModifier.modifyGenericRequest(chan4, requestBuilder)
    }

    val request = requestBuilder.build()
    val captchaInfoRawAdapter = moshi.adapter(CaptchaInfoRaw::class.java)

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val captchaResponseHtml = response.body.string()
    if (captchaResponseHtml == null) {
      throw EmptyBodyResponseException()
    }

    if (captchaResponseHtml.contains("https://mcl.spur.us")) {
      if (mcl.isNotBlank()) {
        throw AntibotCheckLoopDetected()
      }

      throw AntibotCheckDetected(
        htmlToLoad = captchaResponseHtml,
        baseUrl = "https://mcl.spur.us",
        name = "https://spur.us"
      )
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

      throw FailedToExtractCaptchaJsonFromHtml()
    }

    try {
      val captchaInfoRaw = withContext(Dispatchers.IO) { captchaInfoRawAdapter.fromJson(captchaInfoRawString) }
      if (captchaInfoRaw == null) {
        throw ParsingException("Failed to parse 4chan captcha json, captchaInfoRawString: ${captchaInfoRawString}")
      }

      return CaptchaResult(
        captchaInfoRaw = captchaInfoRaw,
        captchaInfoRawString = captchaInfoRawString
      )
    } catch (error: Throwable) {
      Logger.error(TAG) {
        "loadCaptcha($chanDescriptor) captchaInfoRawAdapter.fromJson() error: ${error.errorMessageOrClassName()}"
      }

      captchaInfoRawString
        .chunked(256)
        .forEach { captchaChunk -> Logger.e(TAG, "'${captchaChunk}'") }

      throw FailedToExtractCaptchaJsonFromHtml()
    }
  }

  private suspend fun updateCaptchaTicket(
    chanDescriptor: ChanDescriptor,
    captchaResult: CaptchaResult
  ) {
    val chan4CaptchaSettingsSetting = siteManager.bySiteDescriptorAndActive(Chan4.SITE_DESCRIPTOR)
      ?.siteSettingsOrNull(Chan4SiteSettings::class.java)
      ?.captchaSettings
      ?: return

    if (captchaResult.captchaInfoRaw.ticketNeedsToBeRemoved) {
      Logger.debug(TAG) {
        "updateCaptchaTicket($chanDescriptor) ticket is false which means it needs to be removed. " +
          "Actual ticket value: '${captchaResult.captchaInfoRaw.ticket}'"
      }

      var previousTicket: String? = null

      chan4CaptchaSettingsSetting.update { chan4CaptchaSettings ->
        previousTicket = chan4CaptchaSettings.captchaTicket

        chan4CaptchaSettings.copy(
          captchaTicket = null,
          lastRefreshTime = 0L
        )
      }

      if (previousTicket.isNullOrEmpty()) {
        Logger.debug(TAG) { "updateCaptchaTicket($chanDescriptor) ticket was already removed" }
      } else {
        Logger.debug(TAG) { "updateCaptchaTicket($chanDescriptor) removed ticket '${previousTicket}'" }
      }

      return
    }

    val newTicket = captchaResult.captchaInfoRaw.ticketAsString
    if (newTicket.isNullOrBlank()) {
      Logger.debug(TAG) { "updateCaptchaTicket($chanDescriptor) ticked is null or blank" }
      return
    }

    val oldTicket = chan4CaptchaSettingsSetting.read().captchaTicket

    Logger.debug(TAG) {
      "updateCaptchaTicket($chanDescriptor) " +
        "updating currentTicket with '${StringUtils.formatToken(newTicket)}'"
    }

    chan4CaptchaSettingsSetting.update { chan4CaptchaSettings ->
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
    ticket: String?
  ): String {
    return buildString {
      when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          error("Cannot use CompositeCatalogDescriptor here")
        }

        is ChanDescriptor.CatalogDescriptor -> {
          append("https://sys.4chan.org/captcha")
          append("?board=${boardCode}")

          if (ticket.isNotNullNorEmpty()) {
            append("&ticket=${ticket}")
          }
        }

        is ChanDescriptor.ThreadDescriptor -> {
          append("https://sys.4chan.org/captcha")
          append("?board=${boardCode}&thread_id=${chanDescriptor.threadNo}")

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
    @field:Json(name = "error")
    val err: String?,
    @field:Json(name = "pcd_msg")
    val pcdMsg: String?,
    @field:Json(name = "cd")
    val cd: Int?,
    @field:Json(name = "pcd")
    val pcd: Int?,
    @field:Json(name = "cd_until")
    val cooldownUntil: Long?,
    @field:Json(name = "challenge")
    val challenge: String?,
    @field:Json(name = "tasks")
    val tasks: List<CaptchaTaskRaw>?,
    @field:Json(name = "ttl")
    val ttl: Int?,
    @field:Json(name = "ticket")
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
    val ticketNeedsToBeRemoved: Boolean
      get() = (ticket as? Boolean) == false

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

  @JsonClass(generateAdapter = true)
  data class CaptchaTaskRaw(
    @field:Json(name = "str")
    val textTitle: String?,
    @field:Json(name = "img")
    val imageTitle: String?,
    @field:Json(name = "items")
    val items: List<String>
  )

  class FailedToExtractCaptchaJsonFromHtml : ClientException(
    "Failed to extract 4chan captcha json from HTML. " +
      "This is most likely because the captcha format was changed."
  )

  class AntibotCheckDetected(
    val htmlToLoad: String,
    val baseUrl: String,
    name: String
  ) : ClientException("Detected '${name}' anti-bot check. A WebView will be loaded to pass the check.")

  class AntibotCheckLoopDetected : ClientException("Captcha got rejected even after passing SpurUsAntiBotCheck (wtf?!)")

  companion object {
    private const val TAG = "LoadChan4CaptchaUseCase"
  }

}