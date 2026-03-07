package com.github.k1rakishou.chan.core.site.common.vichan

import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.CommonSite.CommonEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite.SimpleHttpUrl
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import okhttp3.HttpUrl

open class VichanEndpoints(
  site: CommonSite,
) : CommonEndpoints(site) {
  protected open val root = SimpleHttpUrl(site.currentDomain)
  protected open val sys = SimpleHttpUrl(site.currentDomain)

  override fun catalog(
    boardDescriptor: BoardDescriptor,
    contentType: SiteEndpoints.ContentType
  ): HttpUrl? {
    return root.builder()
      .s(boardDescriptor.boardCode)
      .s("catalog.json").url()
  }

  override fun thread(
    threadDescriptor: ThreadDescriptor,
    contentType: SiteEndpoints.ContentType,
    archive: Boolean
  ): HttpUrl? {
    return root.builder()
      .s(threadDescriptor.boardCode())
      .s("res")
      .s(threadDescriptor.threadNo.toString() + ".json")
      .url()
  }

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
      .s(arg.get("tim") + ".png")
      .url()
  }

  override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>?): HttpUrl {
    requireNotNull(arg)

    return root.builder()
      .s(boardDescriptor.boardCode)
      .s("src")
      .s(arg.get("tim") + "." + arg.get("ext"))
      .url()
  }

  override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
    requireNotNull(arg)

    val stat = root.builder().s("static")

    if (icon == "country") {
      stat.s("flags").s(arg.get("country_code")!!.lowercase() + ".png")
    }

    return stat.url()
  }

  override fun pages(board: ChanBoard): HttpUrl {
    return root.builder().s(board.boardCode()).s("threads.json").url()
  }

  override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
    return sys.builder().s("post.php").url()
  }

  override fun delete(post: ChanPost): HttpUrl {
    return sys.builder().s("post.php").url()
  }
}
