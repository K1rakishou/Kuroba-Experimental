package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.SiteBase.Companion.secureRandom
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

class Chan4Endpoints : SiteEndpoints {
  private val a = HttpUrl.Builder().scheme("https").host("a.4cdn.org").build()
  private val i = HttpUrl.Builder().scheme("https").host("i.4cdn.org").build()
  private val t = HttpUrl.Builder().scheme("https").host("i.4cdn.org").build()
  private val s = HttpUrl.Builder().scheme("https").host("s.4cdn.org").build()
  val sys4chan = HttpUrl.Builder().scheme("https").host("sys.4chan.org").build()
  private val b = HttpUrl.Builder().scheme("https").host("boards.4chan.org").build()
  private val search = HttpUrl.Builder().scheme("https").host("find.4chan.org").build()

  override fun catalog(boardDescriptor: BoardDescriptor): HttpUrl {
    return a.newBuilder()
      .addPathSegment(boardDescriptor.boardCode)
      .addPathSegment("catalog.json")
      .build()
  }

  override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
    return a.newBuilder()
      .addPathSegment(threadDescriptor.boardCode())
      .addPathSegment("thread")
      .addPathSegment(threadDescriptor.threadNo.toString() + ".json")
      .build()
  }

  override fun catalogHtml(catalogDescriptor: ChanDescriptor.CatalogDescriptor): HttpUrl {
    return "https://boards.4chan.org/${catalogDescriptor.boardDescriptor.boardCode}/".toHttpUrl()
  }

  override fun threadHtml(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
    return ("https://boards.4chan.org/${threadDescriptor.boardDescriptor.boardCode}/thread/" +
      "${threadDescriptor.threadNo}").toHttpUrl()
  }

  override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>): HttpUrl {
    val imageFile = arg["tim"].toString() + "." + arg["ext"]

    return i.newBuilder()
      .addPathSegment(boardDescriptor.boardCode)
      .addPathSegment(imageFile)
      .build()
  }

  override fun thumbnailUrl(
    boardDescriptor: BoardDescriptor,
    spoiler: Boolean,
    customSpoilers: Int,
    arg: Map<String, String>
  ): HttpUrl {
    val boardCode = boardDescriptor.boardCode

    return if (spoiler) {
      val image = s.newBuilder().addPathSegment("image")
      if (customSpoilers >= 0) {
        val i = secureRandom.nextInt(customSpoilers) + 1
        image.addPathSegment("spoiler-${boardCode}$i.png")
      } else {
        image.addPathSegment("spoiler.png")
      }

      image.build()
    } else {
      when (arg["ext"]) {
        "swf" -> (AppConstants.RESOURCES_ENDPOINT + "swf_thumb.png").toHttpUrl()
        else -> t.newBuilder()
          .addPathSegment(boardCode)
          .addPathSegment(arg["tim"].toString() + "s.jpg")
          .build()
      }
    }
  }

  override fun icon(icon: String, arg: Map<String, String>?): HttpUrl? {
    val b = s.newBuilder().addPathSegment("image")

    when (icon) {
      "country" -> {
        val countryCode = requireNotNull(arg?.get("country_code")) { "Bad arg map: $arg" }

        b.addPathSegment("country")
        b.addPathSegment(countryCode.lowercase(Locale.ENGLISH) + ".gif")
      }
      "board_flag" -> {
        val boardFlagCode = requireNotNull(arg?.get("board_flag_code")) { "Bad arg map: $arg" }
        val boardCode = requireNotNull(arg.get("board_code")) { "Bad arg map: $arg" }

        b.addPathSegment("flags")
        b.addPathSegment(boardCode)
        b.addPathSegment(boardFlagCode.lowercase(Locale.ENGLISH) + ".gif")
      }
      "since4pass" -> b.addPathSegment("minileaf.gif")
    }

    return b.build()
  }

  override fun boards(): HttpUrl {
    return a.newBuilder().addPathSegment("boards.json").build()
  }

  override fun pages(board: ChanBoard): HttpUrl {
    return a.newBuilder()
      .addPathSegment(board.boardCode())
      .addPathSegment("threads.json")
      .build()
  }

  override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
    return sys4chan
      .newBuilder()
      .addPathSegment(chanDescriptor.boardCode())
      .addPathSegment("post")
      .build()
  }

  override fun delete(post: ChanPost): HttpUrl {
    val boardCode = post.boardDescriptor.boardCode

    return sys4chan
      .newBuilder()
      .addPathSegment(boardCode)
      .addPathSegment("imgboard.php")
      .build()
  }

  override fun report(post: ChanPost): HttpUrl {
    val boardCode = post.boardDescriptor.boardCode

    return sys4chan
      .newBuilder()
      .addPathSegment(boardCode)
      .addPathSegment("imgboard.php")
      .addQueryParameter("mode", "report")
      .addQueryParameter("no", post.postNo().toString())
      .build()
  }

  override fun login(): HttpUrl {
    return sys4chan
      .newBuilder()
      .addPathSegment("auth")
      .build()
  }

  override fun search(): HttpUrl {
    return search
  }

  override fun boardArchive(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
    return b.newBuilder()
      .addPathSegment(boardDescriptor.boardCode)
      .addPathSegment("archive")
      .build()
  }
}
