package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

object SharingUtils {
  private const val TAG = "SharingUtils"

  @JvmStatic
  fun getUrlForSharing(siteManager: SiteManager, chanDescriptor: ChanDescriptor): String? {
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      Logger.e(TAG, "getUrlForSharing() site == null (siteDescriptor = ${chanDescriptor.siteDescriptor()})")
      return null
    }

    return site.resolvable().desktopUrl(chanDescriptor, null)
  }

}