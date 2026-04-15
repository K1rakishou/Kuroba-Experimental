package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.site.SiteEndpoints.ContentType
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl

class DvachEndpoints(
  private val dvach: Dvach
) : VichanEndpoints(dvach) {
  val siteHost: String
    get() = dvach.currentDomain.host

  override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>?): HttpUrl {
    requireNotNull(arg)
    val path = requireNotNull(arg["path"]) { "\"path\" parameter not found" }

    return root.builder().s(path).url()
  }

  override fun thumbnailUrl(
    boardDescriptor: BoardDescriptor,
    spoiler: Boolean,
    customSpoilers: Int,
    arg: Map<String, String>?
  ): HttpUrl {
    requireNotNull(arg)
    val thumbnail = requireNotNull(arg["thumbnail"]) { "\"thumbnail\" parameter not found" }

    return root.builder().s(thumbnail).url()
  }

  override fun boards(): HttpUrl {
    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment("api")
      .addPathSegment("mobile")
      .addPathSegment("v2")
      .addPathSegment("boards")
      .build()
  }

  override fun thread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    contentType: ContentType,
    archive: Boolean
  ): HttpUrl? {
    if (archive && contentType == ContentType.Json) {
      // https://2ch.hk/board_code/arch/res/thread_no.json
      return HttpUrl.Builder()
        .scheme("https")
        .host(siteHost)
        .addPathSegment(threadDescriptor.boardCode())
        .addPathSegment("arch")
        .addPathSegment("res")
        .addPathSegment("${threadDescriptor.threadNo}.json")
        .build()
    }

    return super.thread(threadDescriptor, contentType, archive)
  }

  // /api/mobile/v2/after/{board}/{thread}/{num}
  override fun threadPartial(afterPost: PostDescriptor, contentType: ContentType): HttpUrl {
    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment("api")
      .addPathSegment("mobile")
      .addPathSegment("v2")
      .addPathSegment("after")
      .addPathSegment(afterPost.boardDescriptor().boardCode)
      .addPathSegment(afterPost.getThreadNo().toString())
      .addPathSegment(afterPost.postNo.toString())
      .build()
  }

  override fun pages(board: ChanBoard): HttpUrl {
    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment(board.boardCode())
      .addPathSegment("catalog.json")
      .build()
  }

  override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment("user")
      .addPathSegment("posting")
      .build()
  }

  override fun login(): HttpUrl {
    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment("user")
      .addPathSegment("passlogin")
      .addQueryParameter("json", "1")
      .build()
  }

  override fun passCodeInfo(): HttpUrl? {
    if (!dvach.actions.isLoggedIn()) {
      return null
    }

    val passcode = dvach.dvachSettings.passCode.readBlocking()
    if (passcode.isEmpty()) {
      return null
    }

    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment("makaba")
      .addPathSegment("makaba.fcgi")
      .addQueryParameter("task", "auth")
      .addQueryParameter("usercode", passcode)
      .addQueryParameter("json", "1")
      .build()
  }

  override fun search(): HttpUrl {
    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment("user")
      .addPathSegment("search")
      .addQueryParameter("json", "1")
      .build()
  }

  override fun boardArchive(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
    val builder = HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment(boardDescriptor.boardCode)
      .addPathSegment("arch")

    if (page != null) {
      builder.addPathSegment("${page}.html")
    }

    return builder.build()
  }

  override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
    requireNotNull(arg)

    return HttpUrl.Builder()
      .scheme("https")
      .host(siteHost)
      .addPathSegment(requireNotNull(arg.get("icon")) { "Bad arg map: $arg" })
      .build()
  }
}
