package com.github.k1rakishou.chan.core.site.http.login

sealed class DvachLoginResponse(authCookie: String?) : AbstractLoginResponse(authCookie) {
  override fun isSuccess(): Boolean = this is DvachLoginResponse.Success
  override fun successMessage(): String? = (this as? DvachLoginResponse.Success)?.successMessage
  override fun errorMessage(): String? = (this as? DvachLoginResponse.Failure)?.errorMessage

  class Success(val successMessage: String, authCookie: String) : DvachLoginResponse(authCookie)
  class Failure(val errorMessage: String) : DvachLoginResponse(null)
}