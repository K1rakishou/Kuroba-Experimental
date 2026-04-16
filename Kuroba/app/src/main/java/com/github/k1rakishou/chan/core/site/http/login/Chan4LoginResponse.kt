package com.github.k1rakishou.chan.core.site.http.login


sealed class Chan4LoginResponse(authCookie: String?) : AbstractLoginResponse(authCookie) {
  override fun isSuccess(): Boolean = this is Success
  override fun successMessage(): String? = (this as? Success)?.successMessage
  override fun errorMessage(): String? = (this as? Failure)?.errorMessage

  class Success(val successMessage: String, authCookie: String) : Chan4LoginResponse(authCookie)
  class Failure(val errorMessage: String) : Chan4LoginResponse(null)
}