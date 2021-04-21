package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.common.StringUtils

internal sealed class AntiCaptchaServiceResult {
  data class WaitNextIteration(val waitTimeMs: Long) : AntiCaptchaServiceResult()

  object ExitLoop : AntiCaptchaServiceResult() {
    override fun toString(): String {
      return "Exit"
    }
  }

  class AlreadyHaveToken(val token: String?) : AntiCaptchaServiceResult() {
    override fun toString(): String {
      if (token == null) {
        return "AlreadyHaveToken(token='null')"
      }

      return "AlreadyHaveToken(token='${StringUtils.trimCaptchaResponseToken(token)}')"
    }
  }

  class Solution(val token: String) : AntiCaptchaServiceResult() {
    override fun toString(): String {
      return "Solution(token='${StringUtils.trimCaptchaResponseToken(token)}')"
    }
  }

}