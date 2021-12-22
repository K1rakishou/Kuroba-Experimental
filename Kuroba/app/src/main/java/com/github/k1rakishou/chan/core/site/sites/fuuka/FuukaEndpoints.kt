package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import okhttp3.HttpUrl

class FuukaEndpoints(
  site: CommonSite,
  private val rootUrl: HttpUrl
) : CommonSite.CommonEndpoints(site) {

  override fun catalog(boardDescriptor: BoardDescriptor?): HttpUrl {
    throw IllegalStateException("Catalog is not supported by ${site.name()}")
  }

  override fun catalogPage(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
    throw IllegalStateException("CatalogPage is not supported by ${site.name()}")
  }

  // https://warosu.org/g/thread/72382313
  override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
    return rootUrl.newBuilder()
      .addPathSegment(threadDescriptor.boardCode())
      .addPathSegment("thread")
      .addPathSegment(threadDescriptor.threadNo.toString())
      .build()
  }

  override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>): HttpUrl {
    throw NotImplementedError("imageUrl")
  }

  override fun thumbnailUrl(
    boardDescriptor: BoardDescriptor,
    spoiler: Boolean,
    customSpoilers: Int,
    arg: Map<String, String>
  ): HttpUrl {
    throw NotImplementedError("thumbnailUrl")
  }

  override fun search(): HttpUrl {
    return rootUrl
  }

  override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
    throw NotImplementedError("icon")
  }

  override fun pages(board: ChanBoard): HttpUrl {
    throw NotImplementedError("pages")
  }

  override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
    throw NotImplementedError("reply")
  }

  override fun delete(post: ChanPost): HttpUrl {
    throw NotImplementedError("delete")
  }

  override fun login(): HttpUrl {
    throw NotImplementedError("login")
  }

}