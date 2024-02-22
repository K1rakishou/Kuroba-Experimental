package com.github.k1rakishou.chan.features.bypass

sealed class CookieResult {
  data object NotSupported : CookieResult()
  data class CookieValue(val cookie: String) : CookieResult()
  data class Error(val exception: BypassException) : CookieResult()
  data object Canceled : CookieResult()
}