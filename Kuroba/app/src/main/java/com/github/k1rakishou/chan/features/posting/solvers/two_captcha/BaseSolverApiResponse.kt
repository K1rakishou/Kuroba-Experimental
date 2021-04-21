package com.github.k1rakishou.chan.features.posting.solvers.two_captcha

import com.google.gson.annotations.SerializedName

data class BaseSolverApiResponse(
  @SerializedName("status")
  val status: Int,
  @SerializedName("request")
  val requestRaw: String,
  @SerializedName("error_text")
  val errorText: String?
) {
  fun isOk(): Boolean = status == 1

  fun errorTextOrDefault(): String = errorText ?: "No error text"
}