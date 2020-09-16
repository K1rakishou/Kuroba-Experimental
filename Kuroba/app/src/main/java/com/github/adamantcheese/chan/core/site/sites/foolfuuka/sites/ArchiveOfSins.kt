package com.github.adamantcheese.chan.core.site.sites.foolfuuka.sites

import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteIcon
import com.github.adamantcheese.chan.core.site.common.FoolFuukaCommentParser
import com.github.adamantcheese.chan.core.site.sites.foolfuuka.FoolFuukaActions
import com.github.adamantcheese.chan.core.site.sites.foolfuuka.FoolFuukaApi
import com.github.adamantcheese.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.adamantcheese.model.data.archive.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ArchiveOfSins : BaseFoolFuukaSite() {

  override fun rootUrl(): HttpUrl = ROOT_URL

  override fun setup() {
    // Disabled for now, https does not work and Android won't allow to use non-https links
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
      get() = "https://archiveofsins.com/favicon.ico".toHttpUrl()
    override val ROOT: String
      get() = "https://archiveofsins.com/"
    override val ROOT_URL: HttpUrl
      get() =  ROOT.toHttpUrl()
    override val SITE_NAME: String
      get() = ArchiveType.ArchiveOfSins.domain
    override val MEDIA_HOSTS: Array<String>
      get() = arrayOf(ROOT_URL.toString())
    override val NAMES: Array<String>
      get() = arrayOf("archiveofsins")
    override val CLASS: Class<out Site>
      get() = ArchiveOfSins::class.java
  }

}