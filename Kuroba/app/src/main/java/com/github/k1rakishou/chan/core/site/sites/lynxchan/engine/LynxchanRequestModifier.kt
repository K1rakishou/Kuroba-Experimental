package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.common.AppConstants

open class LynxchanRequestModifier(
  site: LynxchanSite,
  appConstants: AppConstants
) : SiteRequestModifier<LynxchanSite>(site, appConstants) {

}