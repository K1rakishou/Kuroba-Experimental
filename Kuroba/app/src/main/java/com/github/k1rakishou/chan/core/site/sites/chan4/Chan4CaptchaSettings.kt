package com.github.k1rakishou.chan.core.site.sites.chan4

import com.google.gson.annotations.SerializedName

data class Chan4CaptchaSettings(
  @SerializedName("captcha_help_shown_v2")
  val captchaHelpShown: Boolean = false,
  @SerializedName("slider_captcha_use_contrast_background")
  val sliderCaptchaUseContrastBackground: Boolean = true,
  @SerializedName("remember_captcha_cookies")
  val rememberCaptchaCookies: Boolean = true,
  @SerializedName("use_captcha_solver")
  val useCaptchaSolver: Boolean = false,
  @SerializedName("captcha_ticket")
  val captchaTicket: String? = null
)