package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaActions
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaApi
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class WakarimasenMoe: BaseFoolFuukaSite() {

  override fun rootUrl(): HttpUrl = ROOT_URL

  override fun siteGlobalSearchType(): SiteGlobalSearchType = SiteGlobalSearchType.FoolFuukaSearch

  private val wakarimasenEndpoints = object : FoolFuukaEndpoints(this@WakarimasenMoe, rootUrl()) {

    // https://archived.moe/_/api/chan/thread/?board=a&num=208364509
    override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
      return archivesManager.getRequestLink(
        ArchiveType.WakarimasenMoe,
        threadDescriptor.boardCode(),
        threadDescriptor.threadNo
      ).toHttpUrl()
    }

  }

  override fun setup() {
    super.setup()

    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, FAVICON_URL))
    setBoardsType(Site.BoardsType.DYNAMIC)
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {})
    setEndpoints(wakarimasenEndpoints)
    setActions(FoolFuukaActions(this))
    setApi(FoolFuukaApi(this))
    setParser(FoolFuukaCommentParser(archivesManager))
  }

  companion object {
    val FAVICON_URL: HttpUrl = "https://archive.wakarimasen.moe/favicon.ico".toHttpUrl()
    val ROOT: String = "https://archive.wakarimasen.moe/"
    val ROOT_URL: HttpUrl = ROOT.toHttpUrl()
    val SITE_NAME: String = ArchiveType.WakarimasenMoe.domain
    val MEDIA_HOSTS: Array<HttpUrl> = arrayOf(ROOT_URL)
    val NAMES: Array<String> = arrayOf("WakarimasenMoe")
    val CLASS: Class<out Site> = WakarimasenMoe::class.java

    val URL_HANDLER = BaseFoolFuukaUrlHandler(ROOT_URL, MEDIA_HOSTS, NAMES, CLASS)
  }

}