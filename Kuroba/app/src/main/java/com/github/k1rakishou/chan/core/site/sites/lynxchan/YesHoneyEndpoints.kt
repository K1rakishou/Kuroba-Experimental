package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanEndpoints
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class YesHoneyEndpoints(lynxchanSite: LynxchanSite) : LynxchanEndpoints(lynxchanSite) {

  override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        return "${lynxchanDomain}/newThread.js?json=1".toHttpUrl()
      }
      is ChanDescriptor.ThreadDescriptor -> {
        return "${lynxchanDomain}/replyThread.js?json=1".toHttpUrl()
      }
    }
  }
}