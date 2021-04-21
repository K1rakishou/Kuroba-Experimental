package com.github.k1rakishou.chan.features.posting.solvers.two_captcha

data class ActiveCaptchaRequest(
  val requestId: Long
) {
  var lastSolutionCheckTime: Long = 0L
}