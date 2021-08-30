package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

abstract class BaseFuukaSite : CommonSite() {
  abstract fun rootUrl(): HttpUrl

  final override fun commentParserType(): CommentParserType = CommentParserType.FuukaParser

  open class BaseFoolFuukaUrlHandler(
    override val url: HttpUrl,
    override val mediaHosts: Array<HttpUrl>,
    override val names: Array<String>,
    private val siteClass: Class<out Site>
  ) : CommonSiteUrlHandler() {

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String? {
      // https://warosu.org/
      val baseUrl = url.toString()

      return when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> null
        is ChanDescriptor.CatalogDescriptor -> {
          "${baseUrl}${chanDescriptor.boardCode()}"
        }
        is ChanDescriptor.ThreadDescriptor -> {
          if (postNo == null) {
            // https://warosu.org/g/thread/72382313
            "${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}"
          } else {
            // https://warosu.org/g/thread/72382313#p72382341
            "${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}#p${postNo}"
          }
        }
      }
    }

    override fun getSiteClass(): Class<out Site> = siteClass
  }

}