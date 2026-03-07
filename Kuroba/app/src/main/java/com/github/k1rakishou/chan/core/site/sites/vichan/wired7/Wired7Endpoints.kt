package com.github.k1rakishou.chan.core.site.sites.vichan.wired7

import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import okhttp3.HttpUrl

class Wired7Endpoints(
  commonSite: CommonSite,
) : VichanEndpoints(commonSite) {
  override fun thumbnailUrl(
    boardDescriptor: BoardDescriptor,
    spoiler: Boolean,
    customSpoilers: Int,
    arg: Map<String, String>?
  ): HttpUrl {
    requireNotNull(arg)

    return root.builder()
      .s(boardDescriptor.boardCode)
      .s("thumb")
      .s(arg.get("tn")!!)
      .url()
  }
}
