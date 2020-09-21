package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.common.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaActions
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaApi
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.archive.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class ForPlebs : BaseFoolFuukaSite() {

  override fun rootUrl(): HttpUrl = ROOT_URL

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, FAVICON_URL))
    setBoardsType(Site.BoardsType.INFINITE)
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {})
    setEndpoints(FoolFuukaEndpoints(this, rootUrl()))
    setActions(FoolFuukaActions(this))
    setApi(FoolFuukaApi(this))
    setParser(FoolFuukaCommentParser(mockReplyManager, archivesManager))
  }

  companion object : FoolFuukaSiteStatic {
    init {
      foolFuukaSiteStatic = this
    }

    override val FAVICON_URL: HttpUrl
      get() = "https://archive.4plebs.org/favicon.ico".toHttpUrl()
    override val ROOT: String
      get() = "https://archive.4plebs.org/"
    override val ROOT_URL: HttpUrl
      get() =  ROOT.toHttpUrl()
    override val SITE_NAME: String
      get() = ArchiveType.ForPlebs.domain
    override val MEDIA_HOSTS: Array<String>
      get() = arrayOf(ROOT_URL.toString())
    override val NAMES: Array<String>
      get() = arrayOf("4plebs")
    override val CLASS: Class<out Site>
      get() = ForPlebs::class.java
  }

}