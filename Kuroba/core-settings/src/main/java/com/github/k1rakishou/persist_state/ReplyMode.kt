package com.github.k1rakishou.persist_state

import com.github.k1rakishou.OptionSettingItem

enum class ReplyMode(val modeRaw: String) : OptionSettingItem {
  Unknown("unknown"),

  // Solve captcha beforehand and then post
  ReplyModeSolveCaptchaManually("reply_mode_solve_captcha_manually"),

  // Try to post without solving captcha (some sites do not require captcha)
  ReplyModeSendWithoutCaptcha("reply_mode_send_without_captcha"),

  // Solve captcha automatically by captcha solver and then post
  ReplyModeSolveCaptchaAuto("reply_mode_solve_captcha_auto"),

  // Use the passcode
  ReplyModeUsePasscode("reply_mode_use_passcode");

  override fun getKey(): String = modeRaw

  companion object {
    fun fromString(value: String?) : ReplyMode {
      if (value == null) {
        return ReplyModeSolveCaptchaManually
      }

      return values().firstOrNull { replyMode -> replyMode.modeRaw == value }
        ?: ReplyModeSolveCaptchaManually
    }
  }
}