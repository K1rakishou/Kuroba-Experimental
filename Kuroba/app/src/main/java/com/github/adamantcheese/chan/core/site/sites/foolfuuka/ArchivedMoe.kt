package com.github.adamantcheese.chan.core.site.sites.foolfuuka

import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.Site.BoardsType
import com.github.adamantcheese.chan.core.site.SiteIcon
import com.github.adamantcheese.chan.core.site.common.FoolFuukaCommentParser
import com.github.adamantcheese.model.data.archive.ArchiveType
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ArchivedMoe : BaseFoolFuukaSite() {

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, FAVICON_URL))
    setBoardsType(BoardsType.INFINITE)
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {})
    setEndpoints(FoolFuukaEndpoints(this, rootUrl()))
    setActions(FoolFuukaActions(this))
    setApi(FoolFuukaApi(this))
    setParser(FoolFuukaCommentParser(mockReplyManager, archivesManager))
  }

  override fun rootUrl(): HttpUrl = ROOT_URL

  companion object {
    val FAVICON_URL = "https://archived.moe/favicon.ico".toHttpUrl()
    val ROOT = "https://archived.moe/"
    val ROOT_URL = ROOT.toHttpUrl()
    val SITE_NAME = ArchiveType.ArchivedMoe.domain

    val URL_HANDLER = object : CommonSiteUrlHandler() {
      override val url: HttpUrl
        get() = ROOT_URL
      override val mediaHosts: Array<String>
        get() = arrayOf(ROOT_URL.toString())
      override val names: Array<String>
        get() = arrayOf("archived")

      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
        when (chanDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> {
            return url.newBuilder()
              .addPathSegment(chanDescriptor.boardCode())
              .toString()
          }
          is ChanDescriptor.ThreadDescriptor -> {
            if (postNo == null) {
              // https://archived.moe/a/thread/208364509/
              return url.newBuilder()
                .addPathSegment(chanDescriptor.boardCode())
                .addPathSegment("thread")
                .addPathSegment(chanDescriptor.threadNo.toString())
                .toString()
            } else {
              // https://archived.moe/a/thread/208364509#208364685
              return url.newBuilder()
                .addPathSegment(chanDescriptor.boardCode())
                .addPathSegment("thread")
                .addPathSegment("${chanDescriptor.threadNo}#${postNo}")
                .toString()
            }
          }
        }
      }

      override fun getSiteClass(): Class<out Site>? = ArchivedMoe::class.java
    }
  }
}