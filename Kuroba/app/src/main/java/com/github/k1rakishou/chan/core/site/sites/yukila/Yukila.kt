package com.github.k1rakishou.chan.core.site.sites.yukila

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Yukila : CommonSite() {

  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = false
  )

  override fun setup() {
    setEnabled(false)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, FAVICON_URL))
    setBoardsType(Site.BoardsType.INFINITE)
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {})
    setEndpoints(YukilaEndpoints(this, ROOT_URL))
    setActions(YukilaActions(this))
    setApi(YukilaApi(this))
    setParser(YukilaCommentParser())
  }

  override fun siteGlobalSearchType(): SiteGlobalSearchType = SiteGlobalSearchType.SearchNotSupported

  override fun commentParserType() = CommentParserType.Default

  override fun getChunkDownloaderSiteProperties() = chunkDownloaderSiteProperties

  class YukilaUrlHandler(
    override val url: HttpUrl,
    override val mediaHosts: Array<HttpUrl>,
    override val names: Array<String>
  ) : CommonSiteUrlHandler() {

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
      // https://yuki.la
      val baseUrl = url.toString()

      return when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> "${baseUrl}${chanDescriptor.boardCode()}"
        is ChanDescriptor.ThreadDescriptor -> {
          if (postNo == null) {
            // https://yuki.la/a/217679476
            "${baseUrl}${chanDescriptor.boardCode()}/${chanDescriptor.threadNo}"
          } else {
            // https://yuki.la/a/217679476#p217680133
            "${baseUrl}${chanDescriptor.boardCode()}/${chanDescriptor.threadNo}#p${postNo}"
          }
        }
      }
    }

    override fun getSiteClass(): Class<out Site> = Yukila::class.java

  }

  companion object {
    val FAVICON_URL: HttpUrl = "https://yuki.la/favicon.ico".toHttpUrl()
    val ROOT: String = "https://yuki.la/"
    val ROOT_URL: HttpUrl = ROOT.toHttpUrl()
    val SITE_NAME: String = ArchiveType.Yukila.domain
    val NAMES: Array<String> = arrayOf("yuki.la")
    val CLASS: Class<out Site> = Yukila::class.java

    val MEDIA_HOSTS: Array<HttpUrl> = arrayOf(
      "https://i1.yuki.la/".toHttpUrl(),
      "https://i2.yuki.la/".toHttpUrl()
    )

    val URL_HANDLER = YukilaUrlHandler(ROOT_URL, MEDIA_HOSTS, NAMES)
  }

}