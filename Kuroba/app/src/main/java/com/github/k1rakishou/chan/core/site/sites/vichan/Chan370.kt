package com.github.k1rakishou.chan.core.site.sites.vichan

import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

class Chan370 : BaseVichanSite(
  defaultDomain = "https://370ch.lt/"
) {
  private val boards by lazy {
    buildList {
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "a"), "anime ir manga"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "b"), "apie viską"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "g"), "technologijos ir žaidimai"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "fo"), "fotografija"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "mu"), "muzika"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "int"), "internacionalus"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "t"), "teptukas"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "meta"), "svetainės aptarimas"))
    }
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val urlHandler by lazy { Chan370UrlHandler(this) }
  override val endpoints by lazy { Chan370Endpoints(this) }
  override val staticBoards: List<ChanBoard> = boards

  class Chan370Endpoints(
    chan370: Chan370
  ) : VichanEndpoints(chan370) {
    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: Map<String, String>?
    ): HttpUrl {
      requireNotNull(arg)

      val extension = when (arg.get("ext")) {
        "jpg", "jpeg" -> "." + arg.get("ext")
        "webm", "mp4", "gif" -> ".gif"
        else -> ".png"
      }

      return site.currentDomain.newBuilder()
        .addPathSegment(boardDescriptor.boardCode)
        .addPathSegment("thumb")
        .addPathSegment(arg.get("tim") + extension)
        .build()
    }
  }

  class Chan370UrlHandler(
    chan370: Chan370
  ) : CommonSiteUrlHandler(chan370) {
    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          return rootUrl.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          return rootUrl.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .addPathSegment("res")
            .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
            .toString()
        }
        else -> {
          return null
        }
      }
    }
  }

  companion object {
    const val SITE_NAME: String = "370chan"
  }
}