package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution

internal sealed class AntiCaptchaServiceResult {
  data class WaitNextIteration(val waitTimeMs: Long) : AntiCaptchaServiceResult()

  object ExitLoop : AntiCaptchaServiceResult() {
    override fun toString(): String {
      return "Exit"
    }
  }

  class AlreadyHaveSolution(val solution: CaptchaSolution?) : AntiCaptchaServiceResult() {
    override fun toString(): String {
      if (solution == null) {
        return "AlreadyHaveSolution(solution='null')"
      }

      return "AlreadyHaveSolution(solution='${solution}')"
    }
  }

  class Solution(val solution: CaptchaSolution) : AntiCaptchaServiceResult() {
    override fun toString(): String {
      return "Solution(solution='${solution}')"
    }
  }

}