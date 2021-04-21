package com.github.k1rakishou.chan.features.posting.solvers.two_captcha

class TwoCaptchaBalanceResponse private constructor(
  val response: BaseSolverApiResponse
) {
  val balance: Float? by lazy {
    if (response.isOk()) {
      return@lazy response.requestRaw.toFloatOrNull()
    }

    return@lazy null
  }

  fun isOk(): Boolean = response.isOk()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TwoCaptchaBalanceResponse

    if (response != other.response) return false

    return true
  }

  override fun hashCode(): Int {
    return response.hashCode()
  }

  override fun toString(): String {
    return "TwoCaptchaBalanceResponse(response=$response)"
  }

  companion object {
    fun wrap(response: BaseSolverApiResponse): TwoCaptchaBalanceResponse {
      return TwoCaptchaBalanceResponse(response)
    }
  }
}