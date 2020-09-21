package com.github.k1rakishou.chan.core.site.http.login

abstract class AbstractLoginResponse(
  val authCookie: String?
) {
  abstract fun successMessage(): String?
  abstract fun errorMessage(): String?
  abstract fun isSuccess(): Boolean
}