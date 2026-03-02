package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

interface SiteUrlHandler {
  fun getSiteClass(): Class<out Site>
  fun matchesName(value: String): Boolean
  fun respondsTo(url: HttpUrl): Boolean
  fun matchesMediaHost(url: HttpUrl): Boolean
  fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String?
  fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor?
}