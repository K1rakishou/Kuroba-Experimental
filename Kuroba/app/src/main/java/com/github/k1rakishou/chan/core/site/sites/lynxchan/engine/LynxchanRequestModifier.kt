package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.common.AppConstants
import okhttp3.Request

open class LynxchanRequestModifier(
  site: LynxchanSite,
  appConstants: AppConstants
) : SiteRequestModifier<LynxchanSite>(site, appConstants) {

  override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    super.modifyHttpCall(httpCall, requestBuilder)
  }

  override fun modifyCaptchaGetRequest(site: LynxchanSite, requestBuilder: Request.Builder) {
    super.modifyCaptchaGetRequest(site, requestBuilder)
  }

}