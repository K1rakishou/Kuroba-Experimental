package com.github.k1rakishou.v2.parameters

enum class ReplyMode {
  Unknown,

  // Solve captcha beforehand and then post
  ReplyModeSolveCaptchaManually,

  // Try to post without solving captcha (some sites do not require captcha)
  ReplyModeSendWithoutCaptcha,

  // Solve captcha automatically by captcha solver and then post
  ReplyModeSolveCaptchaAuto,

  // Use the passcode
  ReplyModeUsePasscode;

  companion object {
    fun fromString(value: String?) : ReplyMode {
      if (value == null) {
        return ReplyModeSolveCaptchaManually
      }

      return entries.firstOrNull { replyMode -> replyMode.name == value }
        ?: ReplyModeSolveCaptchaManually
    }
  }
}