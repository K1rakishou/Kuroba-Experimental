package com.github.k1rakishou.chan.core.site.common.taimaba

import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.CommonSite.CommonEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite.SimpleHttpUrl
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class TaimabaEndpoints(
  commonSite: CommonSite,
  rootUrl: String,
  sysUrl: String
) : CommonEndpoints(commonSite) {
  private val root = SimpleHttpUrl(rootUrl)
  private val sys = SimpleHttpUrl(sysUrl)

  private val report = HttpUrl.Builder()
    .scheme("https")
    .host("cdn.420chan.org")
    .port(8443)
    .build()

  override fun catalog(
    boardDescriptor: BoardDescriptor,
    contentType: SiteEndpoints.ContentType
  ): HttpUrl? {
    return root.builder()
      .s(boardDescriptor.boardCode)
      .s("catalog.json")
      .url()
  }

  override fun boards(): HttpUrl {
    return root.builder().s("boards.json").url()
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
  ): HttpUrl? {
    requireNotNull(arg)

    return when (arg["ext"]) {
      "swf" -> (AppConstants.RESOURCES_ENDPOINT + "swf_thumb.png").toHttpUrlOrNull()
      "mp3", "m4a", "ogg", "flac" -> (AppConstants.RESOURCES_ENDPOINT + "audio_thumb.png").toHttpUrlOrNull()
      else -> sys.builder()
        .s(boardDescriptor.boardCode)
        .s("thumb")
        .s(arg["tim"] + "s.jpg")
        .url()
    }
  }

  override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>?): HttpUrl {
    requireNotNull(arg)

    return sys.builder()
      .s(boardDescriptor.boardCode)
      .s("src")
      .s(arg.get("tim") + "." + arg.get("ext"))
      .url()
  }

  override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
    requireNotNull(arg)
    val stat = sys.builder().s("static")

    if (icon == "country") {
      stat.s("flags").s(arg.get("country_code")!!.lowercase() + ".png")
    }

    return stat.url()
  }

  override fun pages(board: ChanBoard): HttpUrl {
    return root.builder().s(board.boardCode()).s("threads.json").url()
  }

  override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
    return sys.builder().s(chanDescriptor.boardCode()).s("taimaba.pl").url()
  }

  override fun report(post: ChanPost): HttpUrl {
    return report.newBuilder()
      .addPathSegment("narcbot")
      .addPathSegment("ajaxReport.jsp")
      .addQueryParameter("postId", post.postNo().toString())
      .addQueryParameter("reason", "RULE_VIOLATION")
      .addQueryParameter("note", "")
      .addQueryParameter(
        "location",
        "http://boards.420chan.org/" + post.boardDescriptor.boardCode + "/" + post.postNo()
      )
      .build()
  }
}
