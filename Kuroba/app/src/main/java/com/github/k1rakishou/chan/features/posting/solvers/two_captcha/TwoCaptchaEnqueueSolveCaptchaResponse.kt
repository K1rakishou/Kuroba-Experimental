package com.github.k1rakishou.chan.features.posting.solvers.two_captcha

class TwoCaptchaEnqueueSolveCaptchaResponse private constructor(
  val response: BaseSolverApiResponse
) {
  val requestId: Long? by lazy {
    if (response.isOk()) {
      return@lazy response.requestRaw.toLongOrNull()
    }

    return@lazy null
  }

  fun badApiKey(): Boolean = response.requestRaw == "ERROR_KEY_DOES_NOT_EXIST"
  fun badUrl(): Boolean = response.requestRaw == "ERROR_PAGEURL"
  fun badSiteKey(): Boolean = response.requestRaw == "ERROR_GOOGLEKEY"
  fun isZeroBalanceError(): Boolean = response.requestRaw == "ERROR_ZERO_BALANCE"
  fun noAvailableSlots(): Boolean = response.requestRaw == "ERROR_NO_SLOT_AVAILABLE"

  fun isOk(): Boolean = response.isOk()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TwoCaptchaEnqueueSolveCaptchaResponse

    if (response != other.response) return false

    return true
  }

  override fun hashCode(): Int {
    return response.hashCode()
  }

  override fun toString(): String {
    return "TwoCaptchaEnqueueSolveCaptchaResponse(response=$response)"
  }

  companion object {
    fun wrap(response: BaseSolverApiResponse): TwoCaptchaEnqueueSolveCaptchaResponse {
      return TwoCaptchaEnqueueSolveCaptchaResponse(response)
    }
  }
}