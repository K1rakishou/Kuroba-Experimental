package com.github.k1rakishou.chan.core.site.http.login

sealed class DvachLoginResponse(authCookie: String?) : AbstractLoginResponse(authCookie) {
  override fun isSuccess(): Boolean = this is Success
  override fun successMessage(): String? = (this as? Success)?.successMessage
  override fun errorMessage(): String? = (this as? Failure)?.errorMessage

  class Success(val successMessage: String, authCookie: String) : DvachLoginResponse(authCookie)
  class Failure(val errorMessage: String) : DvachLoginResponse(null)
}