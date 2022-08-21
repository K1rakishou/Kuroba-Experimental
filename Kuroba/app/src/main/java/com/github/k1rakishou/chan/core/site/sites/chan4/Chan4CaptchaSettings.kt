package com.github.k1rakishou.chan.core.site.sites.chan4

import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit

data class Chan4CaptchaSettings(
  @SerializedName("cookie_received_on")
  val cookieReceivedOn: Long = 0L,
  @SerializedName("captcha_help_shown_v2")
  val captchaHelpShown: Boolean = false,
  @SerializedName("slider_captcha_use_contrast_background")
  val sliderCaptchaUseContrastBackground: Boolean = true,
  @SerializedName("remember_captcha_cookies")
  val rememberCaptchaCookies: Boolean = true,
  @SerializedName("use_captcha_solver")
  val useCaptchaSolver: Boolean = false
) {

  companion object {
    val COOKIE_LIFE_TIME = TimeUnit.DAYS.toMillis(180)
  }
}