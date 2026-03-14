package com.github.k1rakishou.chan.core.site.sites.chan4

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Chan4CaptchaSettings(
  @field:Json("remember_captcha_cookies")
  val rememberCaptchaCookies: Boolean = true,
  @field:Json("captcha_ticket")
  val captchaTicket: String? = null,
  @field:Json("last_refresh_time")
  val lastRefreshTime: Long = 0L
)