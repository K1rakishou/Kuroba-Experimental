package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanReplyHttpCall
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Moshi
import okhttp3.HttpUrl

class Endchan : BaseLynxchanSite(
  defaultDomain = "https://endchan.net"
) {
  override val name: String = SITE_NAME
  override val urlHandler by lazy { EndchanUrlHandler(this, mediaHosts) }

  override fun createReplyHttpCall(
    replyChanDescriptor: ChanDescriptor,
    replyManager: ReplyManager,
    moshi: Moshi
  ): BaseLynxchanReplyHttpCall {
    return EndchanReplyHttpCall(
      site = this,
      replyChanDescriptor = replyChanDescriptor,
      replyManager = replyManager,
      moshi = moshi
    )
  }

  class EndchanUrlHandler(
    site: BaseLynxchanSite,
    mediaHosts: Set<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    site = site,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "Endchan"
  }
}