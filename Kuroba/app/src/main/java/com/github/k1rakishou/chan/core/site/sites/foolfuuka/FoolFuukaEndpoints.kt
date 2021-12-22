package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import okhttp3.HttpUrl

open class FoolFuukaEndpoints(
  site: CommonSite,
  protected val rootUrl: HttpUrl
) : CommonSite.CommonEndpoints(site) {

  override fun catalog(boardDescriptor: BoardDescriptor?): HttpUrl {
    throw IllegalStateException("Catalog is not supported by ${site.name()}")
  }

  // https://archived.moe/a/
  // https://archived.moe/a/page/2/
  override fun catalogPage(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
    val builder = rootUrl.newBuilder()
      .addPathSegment(boardDescriptor.boardCode)

    if (page != null && page >= 0) {
      builder
        .addPathSegment("page")
        .addPathSegment(page.toString())
    }

    return builder.build()
  }

  // https://archived.moe/_/api/chan/thread/?board=a&num=208364509
  override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
    return rootUrl.newBuilder()
      .addPathSegments("_/api/chan/thread")
      .addQueryParameter("board", threadDescriptor.boardCode())
      .addQueryParameter("num", threadDescriptor.threadNo.toString())
      .build()
  }

  override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>): HttpUrl {
    throw NotImplementedError("imageUrl")
  }

  // https://archived.moe/files/a/thumb/1599/43/1599433446565s.jpg
  override fun thumbnailUrl(boardDescriptor: BoardDescriptor, spoiler: Boolean, customSpoilers: Int, arg: Map<String, String>): HttpUrl {
    val param1 = requireNotNull(arg[THUMBNAIL_PARAM_1]) { "THUMBNAIL_PARAM_1_NAME not provided" }
    val param2 = requireNotNull(arg[THUMBNAIL_PARAM_2]) { "THUMBNAIL_PARAM_2_NAME not provided" }
    val fileId = requireNotNull(arg[THUMBNAIL_FILE_ID]) { "THUMBNAIL_FILE_ID not provided" }
    val extension = requireNotNull(arg[THUMBNAIL_EXTENSION]) { "THUMBNAIL_EXTENSION not provided" }

    return rootUrl.newBuilder()
      .addPathSegments("files/a/thumb/$param1/$param2/${fileId}s.${extension}")
      .build()
  }

  override fun boards(): HttpUrl {
    return rootUrl
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

  companion object {
    const val THUMBNAIL_PARAM_1 = "param1"
    const val THUMBNAIL_PARAM_2 = "param2"
    const val THUMBNAIL_FILE_ID = "file_id"
    const val THUMBNAIL_EXTENSION = "extension"
  }
}