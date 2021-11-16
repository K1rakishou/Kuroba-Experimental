package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonSite


open class LynxchanConfig : CommonSite.CommonConfig() {
  override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
    return siteFeature == Site.SiteFeature.POSTING
  }
}