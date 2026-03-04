package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.site.sites.dvach.DvachApi

sealed class SiteSpecificError {
  abstract fun isNotFoundError(): Boolean

  data class DvachError(
    val errorCode: Int,
    val errorMessage: String
  ) : SiteSpecificError() {

    override fun isNotFoundError(): Boolean {
      return DvachApi.DvachError.isNotFoundError(errorCode)
    }

  }

}