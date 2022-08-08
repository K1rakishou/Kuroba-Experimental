package com.github.k1rakishou.chan.features.bypass

sealed class CookieResult {
  object NotSupported : CookieResult()
  data class CookieValue(val cookie: String) : CookieResult()
  data class Error(val exception: BypassExceptions) : CookieResult()
  object Canceled : CookieResult()
}