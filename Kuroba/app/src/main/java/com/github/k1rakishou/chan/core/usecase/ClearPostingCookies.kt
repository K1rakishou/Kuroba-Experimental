package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class ClearPostingCookies(
  private val siteManager: SiteManager
) {

  fun perform(siteDescriptor: SiteDescriptor) {
    Logger.debug(TAG) { "perform(${siteDescriptor})" }

    siteManager.bySiteDescriptor(siteDescriptor)
      ?.actions()
      ?.clearPostingCookies()
  }

  companion object {
    private const val TAG = "ClearPostingCookies"
  }

}