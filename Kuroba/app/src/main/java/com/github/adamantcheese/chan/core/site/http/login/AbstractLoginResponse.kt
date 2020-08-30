package com.github.adamantcheese.chan.core.site.http.login

abstract class AbstractLoginResponse(
  val authCookie: String?
) {
  abstract fun successMessage(): String?
  abstract fun errorMessage(): String?
  abstract fun isSuccess(): Boolean
}