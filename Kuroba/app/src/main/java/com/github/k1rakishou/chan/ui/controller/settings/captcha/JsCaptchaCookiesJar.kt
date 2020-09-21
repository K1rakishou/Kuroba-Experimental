package com.github.k1rakishou.chan.ui.controller.settings.captcha

import com.github.k1rakishou.common.DoNotStrip
import com.google.gson.annotations.SerializedName

@DoNotStrip
data class JsCaptchaCookiesJar(
  @SerializedName("hsid_cookie")
  val hsidCookie: String = "",
  @SerializedName("ssid_cookie")
  val ssidCookie: String = "",
  @SerializedName("sid_cookie")
  val sidCookie: String = "",
  @SerializedName("nid_cookie")
  val nidCookie: String = ""
) {

  fun isValid(): Boolean {
    return hsidCookie.isNotEmpty()
      && ssidCookie.isNotEmpty()
      && sidCookie.isNotEmpty()
      && nidCookie.isNotEmpty()
  }

  fun getCookies(): Array<String> {
    return arrayOf(
      "HSID=$hsidCookie$COOKIE_SUFFIX",
      "SSID=$ssidCookie$COOKIE_SUFFIX",
      "SID=$sidCookie$COOKIE_SUFFIX",
      "NID=$nidCookie$COOKIE_SUFFIX"
    )
  }

  companion object {
    @JvmStatic
    fun empty(): JsCaptchaCookiesJar {
      return JsCaptchaCookiesJar(
        hsidCookie = "",
        ssidCookie = "",
        sidCookie = "",
        nidCookie = ""
      )
    }

    private const val COOKIE_SUFFIX = "; path=/; domain=.google.com"
  }
}