package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.ResolvedChanDescriptor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteBase.Companion.containsMediaHostUrl
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Chan4UrlHandler : SiteUrlHandler {
  private val hosts = setOf(
    "4chan.org",
    "boards.4chan.org",
    "sys.4chan.org",
    "find.4chan.org",
    "a.4cdn.org",
    "i.4cdn.org",
    "s.4cdn.org",
  )

  private val mediaHosts = setOf(
    "https://i.4cdn.org/".toHttpUrl(),
    "https://is2.4chan.org/".toHttpUrl(),
  )

  override fun matchesMediaHost(url: HttpUrl): Boolean {
    return containsMediaHostUrl(url, mediaHosts)
  }

  override fun respondsTo(url: HttpUrl): Boolean {
    val host = url.host.removePrefix("www.")

    return hosts.contains(host)
  }

  override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String {
    if (chanDescriptor.isCatalogDescriptor()) {
      return if (postNo != null && postNo > 0) {
        "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/thread/" + postNo
      } else {
        "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/"
      }
    }

    if (chanDescriptor.isThreadDescriptor()) {
      val threadNo = (chanDescriptor as ChanDescriptor.ThreadDescriptor).threadNo

      var url = "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/thread/" + threadNo
      if (postNo != null && postNo > 0 && threadNo != postNo) {
        url += "#p$postNo"
      }

      return url
    }

    return "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/"
  }

  override fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor? {
    val parts = url.pathSegments
    if (parts.isEmpty()) {
      return null
    }

    val boardCode = parts[0]

    if (parts.size < 3) {
      // Board mode
      return ResolvedChanDescriptor(ChanDescriptor.CatalogDescriptor.create(site.name, boardCode))
    }

    // Thread mode
    val threadNo = (parts[2].toIntOrNull() ?: -1).toLong()
    var postId = -1L
    val fragment = url.fragment

    if (fragment != null) {
      val index = fragment.indexOf("p")
      if (index >= 0) {
        postId = (fragment.substring(index + 1).toIntOrNull() ?: -1).toLong()
      }
    }

    if (threadNo < 0L) {
      return null
    }

    val markedPostNo = if (postId >= 0L) {
      postId
    } else {
      null
    }

    val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      siteName = site.name,
      boardCode = boardCode,
      threadNo = threadNo
    )

    return ResolvedChanDescriptor(threadDescriptor, markedPostNo)
  }
}
