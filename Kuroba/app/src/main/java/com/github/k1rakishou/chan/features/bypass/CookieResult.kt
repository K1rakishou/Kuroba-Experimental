package com.github.k1rakishou.chan.features.bypass

sealed class CookieResult {
  data class CookieValue(val cookie: String) : CookieResult()
  data class Error(val exception: BypassExceptions) : CookieResult()
  object Timeout : CookieResult()
  object Canceled : CookieResult()
}