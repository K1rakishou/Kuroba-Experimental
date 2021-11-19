package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonSite


open class LynxchanConfig(
  private val supportsPosting: Boolean
) : CommonSite.CommonConfig() {
  override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
    if (siteFeature == Site.SiteFeature.POSTING) {
      return supportsPosting
    }

    return false
  }
}