package com.github.k1rakishou.chan.features.posting.solvers.two_captcha

class TwoCaptchaCheckSolutionResponse private constructor(
  val response: BaseSolverApiResponse
) {
  fun isOk(): Boolean = response.isOk()

  fun isCaptchaNotReady(): Boolean = response.requestRaw == "CAPCHA_NOT_READY"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TwoCaptchaCheckSolutionResponse

    if (response != other.response) return false

    return true
  }

  override fun hashCode(): Int {
    return response.hashCode()
  }

  override fun toString(): String {
    return "TwoCaptchaCheckSolutionResponse(response=$response)"
  }

  companion object {
    fun wrap(response: BaseSolverApiResponse): TwoCaptchaCheckSolutionResponse {
      return TwoCaptchaCheckSolutionResponse(response)
    }
  }
}