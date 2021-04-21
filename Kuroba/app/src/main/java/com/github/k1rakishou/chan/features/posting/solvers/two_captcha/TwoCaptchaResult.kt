package com.github.k1rakishou.chan.features.posting.solvers.two_captcha

import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

sealed class TwoCaptchaResult {
  data class BadSiteKey(val solverName: String) : TwoCaptchaResult()
  data class SolverBadApiKey(val solverName: String) : TwoCaptchaResult()
  data class SolverDisabled(val solverName: String) : TwoCaptchaResult()
  data class SolverBadApiUrl(val solverName: String, val url: String) : TwoCaptchaResult()
  data class BadSiteBaseUrl(val solverName: String, val url: String?) : TwoCaptchaResult()
  data class NotSupported(val solverName: String, val siteDescriptor: SiteDescriptor) : TwoCaptchaResult()
  data class CaptchaNotNeeded(val solverName: String, val siteDescriptor: SiteDescriptor) : TwoCaptchaResult()
  data class BadBalance(val balance: Float?) : TwoCaptchaResult()

  data class UnknownError(val message: String) : TwoCaptchaResult()
  data class BadBalanceResponse(val twoCaptchaBalanceResponse: TwoCaptchaBalanceResponse) : TwoCaptchaResult()
  data class BadSolveCaptchaResponse(val twoCaptchaSolveCaptchaResponse: TwoCaptchaEnqueueSolveCaptchaResponse) : TwoCaptchaResult()
  data class BadCheckCaptchaSolutionResponse(val twoCaptchaCheckSolutionResponse: TwoCaptchaCheckSolutionResponse) : TwoCaptchaResult()
  data class WaitingForSolution(val waitTime: Long) : TwoCaptchaResult()
  data class Solution(val twoCaptchaCheckSolutionResponse: TwoCaptchaCheckSolutionResponse): TwoCaptchaResult()
}