package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.site.sites.dvach.DvachApiV2

sealed class SiteSpecificError {
  abstract fun isNotFoundError(): Boolean

  data class DvachError(
    val errorCode: Int,
    val errorMessage: String
  ) : SiteSpecificError() {

    override fun isNotFoundError(): Boolean {
      return DvachApiV2.DvachError.isNotFoundError(errorCode)
    }

  }

}