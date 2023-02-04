package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaActions
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaApi
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class RozenArcana : BaseFoolFuukaSite() {

  override fun rootUrl(): HttpUrl = ROOT_URL

  override fun setup() {
    super.setup()

    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, FAVICON_URL))
    setBoardsType(Site.BoardsType.DYNAMIC)
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {})
    setEndpoints(FoolFuukaEndpoints(this, rootUrl()))
    setActions(FoolFuukaActions(this))
    setApi(FoolFuukaApi(this))
    setParser(FoolFuukaCommentParser(archivesManager))
  }

  companion object {
    val FAVICON_URL: HttpUrl = "https://www.tokyochronos.net/upload/gy9g2krc.png".toHttpUrl()
    val ROOT: String = "https://archive.palanq.win/"
    val ROOT_URL: HttpUrl = ROOT.toHttpUrl()
    val SITE_NAME: String = ArchiveType.RozenArcana.domain
    val MEDIA_HOSTS: Array<HttpUrl> = arrayOf(ROOT_URL)
    val NAMES: Array<String> = arrayOf("archive.alice")
    val CLASS: Class<out Site> = RozenArcana::class.java

    val URL_HANDLER = BaseFoolFuukaUrlHandler(ROOT_URL, MEDIA_HOSTS, NAMES, CLASS)
  }

}