package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.site.ResolvedChanDescriptor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonSite.CommonSiteUrlHandler
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import java.util.regex.Pattern

class DvachSiteUrlHandler(
  private val dvach: Dvach
) : CommonSiteUrlHandler(dvach) {
  override val rootUrl: HttpUrl
    get() = dvach.currentDomain

  override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
    when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> {
        val builtUrl = rootUrl.newBuilder()
          .addPathSegment(chanDescriptor.boardCode())
          .toString()

        if (postNo == null) {
          return builtUrl
        }

        return "${builtUrl}/res/${postNo}.html"
      }
      is ChanDescriptor.ThreadDescriptor -> {
        val builtUrl = rootUrl.newBuilder()
          .addPathSegment(chanDescriptor.boardCode())
          .addPathSegment("res")
          .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
          .toString()

        if (postNo == null) {
          return builtUrl
        }

        return "${builtUrl}#${postNo}"
      }
      else -> return null
    }
  }

  // https://2ch.hk/b/arch/2020-09-16/res/11223344.html#11223345
  override fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor? {
    val threadArchivePattern = ARCHIVE_THREAD_PATTERN.matcher(url.toString())

    if (!threadArchivePattern.find()) {
      return super.resolveChanDescriptor(site, url)
    }

    val boardCode = threadArchivePattern.groupOrNull(1)
      ?: return null
    val threadNo = threadArchivePattern.groupOrNull(2)?.toLongOrNull()
      ?: return null
    val markedPostNo = threadArchivePattern.groupOrNull(3)?.toLongOrNull()

    val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      siteName = site.name,
      boardCode = boardCode,
      threadNo = threadNo
    )

    return ResolvedChanDescriptor(
      chanDescriptor = threadDescriptor,
      markedPostNo = markedPostNo
    )
  }

  companion object {
    private val ARCHIVE_THREAD_PATTERN =
      Pattern.compile("\\/(\\w+)\\/arch\\/\\d+-\\d+-\\d+\\/res\\/(\\d+)\\.html(?:#(\\d+))?")
  }
}
