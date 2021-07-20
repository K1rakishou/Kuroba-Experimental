package com.github.k1rakishou.chan.features.bypass

sealed class CookieResult {
  data class CookieValue(val cookie: String) : CookieResult()
  data class Error(val exception: BypassExceptions) : CookieResult()
  data class Timeout(val timeoutMs: Long) : CookieResult()
  object Canceled : CookieResult()
}