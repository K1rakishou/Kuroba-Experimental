package com.github.adamantcheese.chan.utils

import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

object SharingUtils {
  private const val TAG = "SharingUtils"

  @JvmStatic
  fun getUrlForSharing(siteRepository: SiteRepository, chanDescriptor: ChanDescriptor): String? {
    val site = siteRepository.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      Logger.e(TAG, "getUrlForSharing() site == null (siteDescriptor = ${chanDescriptor.siteDescriptor()})")
      return null
    }

    return site.resolvable().desktopUrl(chanDescriptor, null)
  }

}