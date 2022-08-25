package com.github.k1rakishou.chan.features.bypass

sealed class CookieResult {
  object NotSupported : CookieResult()
  data class CookieValue(val cookie: String) : CookieResult()
  data class Error(val exception: BypassException) : CookieResult()
  object Canceled : CookieResult()
}