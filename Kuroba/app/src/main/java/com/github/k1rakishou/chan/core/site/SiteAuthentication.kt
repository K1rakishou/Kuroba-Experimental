package com.github.k1rakishou.chan.core.site

import okhttp3.HttpUrl

class SiteAuthentication private constructor(val type: Type) {
  // captcha1 & captcha2
  @JvmField
  var siteKey: String? = null
  @JvmField
  var baseUrl: String? = null

  // generic webview
  @JvmField
  var url: String? = null
  @JvmField
  var retryText: String? = null
  @JvmField
  var successText: String? = null

  @JvmField
  var customCaptcha: CustomCaptcha? = null

  enum class Type {
    NONE,
    CAPTCHA2,
    CAPTCHA2_NOJS,
    CAPTCHA2_INVISIBLE,
    GENERIC_WEBVIEW,  // Captcha that can be loaded by a specific url with an ID parameter

    // (For now only 2ch.hk has this type of captcha)
    ID_BASED_CAPTCHA,  // Captcha that can be loaded by a specific url with boardCode/threadId parameters

    // (For now only 4chan.org has this type of captcha).
    ENDPOINT_BASED_CAPTCHA,

    // New 2ch.hk captcha
    EMOJI_CAPTCHA,

    CUSTOM_CAPTCHA
  }

  sealed class CustomCaptcha {
    data class LynxchanCaptcha(
      val captchaEndpoint: HttpUrl,
      val solveCaptchaEndpoint: HttpUrl,
      val bypassEndpoint: HttpUrl,
      val renewBypassEndpoint: HttpUrl,
      val validateBypassEndpoint: HttpUrl,
    ) : CustomCaptcha()
  }

  companion object {
    fun fromNone(): SiteAuthentication {
      return SiteAuthentication(Type.NONE)
    }

    fun fromCaptcha2(siteKey: String?, baseUrl: String?): SiteAuthentication {
      val a = SiteAuthentication(Type.CAPTCHA2)
      a.siteKey = siteKey
      a.baseUrl = baseUrl
      return a
    }

    fun fromCaptcha2nojs(siteKey: String?, baseUrl: String?): SiteAuthentication {
      val a = SiteAuthentication(Type.CAPTCHA2_NOJS)
      a.siteKey = siteKey
      a.baseUrl = baseUrl
      return a
    }

    fun fromCaptcha2Invisible(siteKey: String?, baseUrl: String?): SiteAuthentication {
      val a = SiteAuthentication(Type.CAPTCHA2_INVISIBLE)
      a.siteKey = siteKey
      a.baseUrl = baseUrl
      return a
    }

    fun fromUrl(url: String?, retryText: String?, successText: String?): SiteAuthentication {
      val a = SiteAuthentication(Type.GENERIC_WEBVIEW)
      a.url = url
      a.retryText = retryText
      a.successText = successText
      return a
    }

    fun idBased(idGetUrl: String?): SiteAuthentication {
      val siteAuthentication = SiteAuthentication(Type.ID_BASED_CAPTCHA)
      siteAuthentication.baseUrl = idGetUrl
      return siteAuthentication
    }

    fun emoji(baseUrl: String): SiteAuthentication {
      val siteAuthentication = SiteAuthentication(Type.EMOJI_CAPTCHA)
      siteAuthentication.baseUrl = baseUrl
      return siteAuthentication
    }

    fun endpointBased(): SiteAuthentication {
      return SiteAuthentication(Type.ENDPOINT_BASED_CAPTCHA)
    }

    fun customCaptcha(customCaptcha: CustomCaptcha): SiteAuthentication  {
      return SiteAuthentication(type = Type.CUSTOM_CAPTCHA)
        .also { siteAuthentication -> siteAuthentication.customCaptcha = customCaptcha }
    }

  }
}