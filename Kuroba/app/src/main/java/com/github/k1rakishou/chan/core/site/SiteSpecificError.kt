package com.github.k1rakishou.chan.core.site

sealed class SiteSpecificError {

  data class ErrorCode(
    val errorCode: Int,
    val errorMessage: String
  ) : SiteSpecificError()

}