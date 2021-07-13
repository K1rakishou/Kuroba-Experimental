package com.github.k1rakishou.chan.core.site.sites.chan4

import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit

data class Chan4CaptchaSettings(
  @SerializedName("only_draw_background_image")
  val onlyShowBackgroundImage: Boolean = false,
  @SerializedName("cookie_received_on")
  val cookieReceivedOn: Long = 0L,
  @SerializedName("captcha_help_shown")
  val captchaHelpShown: Boolean = false,
  @SerializedName("slider_captcha_use_contrast_background")
  val sliderCaptchaUseContrastBackground: Boolean = true
) {

  companion object {
    val COOKIE_LIFE_TIME = TimeUnit.DAYS.toMillis(180)
  }
}