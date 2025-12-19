package com.github.k1rakishou.chan.core.site.sites.chan4

import com.google.gson.annotations.SerializedName

data class Chan4CaptchaSettings(
  @SerializedName("remember_captcha_cookies")
  val rememberCaptchaCookies: Boolean = true,
  @SerializedName("captcha_ticket")
  val captchaTicket: String? = null,
  @SerializedName("last_refresh_time")
  val lastRefreshTime: Long = 0L
)