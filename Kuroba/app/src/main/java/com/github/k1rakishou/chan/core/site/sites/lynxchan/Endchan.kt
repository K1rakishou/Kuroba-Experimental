package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class Endchan : LynxchanSite() {
  private val defaultDomain = "https://endchan.net/".toHttpUrl()
  private val siteIconLazy by lazy { SiteIcon.fromFavicon(imageLoaderV2, "https://endchan.net/favicon.ico".toHttpUrl()) }

  override val domain: Lazy<HttpUrl> = lazy {
    val siteDomain = siteDomainSetting?.get()
    if (siteDomain != null) {
      val siteDomainUrl = siteDomain.toHttpUrlOrNull()
      if (siteDomainUrl != null) {
        Logger.d(TAG, "Using domain: \'${siteDomainUrl}\'")
        return@lazy siteDomainUrl
      }
    }

    Logger.d(TAG, "Using default domain: \'${defaultDomain}\'")
    return@lazy defaultDomain
  }

  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", defaultDomain.toString())
  }

  private val mediaHostsLazy = lazy { arrayOf(domain.value) }
  private val siteUrlHandler = lazy { EndchanUrlHandler(domain.value, mediaHostsLazy.value) }

  override val siteName: String
    get() = SITE_NAME
  override val siteIcon: SiteIcon
    get() = siteIconLazy
  override val urlHandler: Lazy<BaseLynxchanUrlHandler>
    get() = siteUrlHandler

  class EndchanUrlHandler(baseUrl: HttpUrl, mediaHosts: Array<HttpUrl>) : BaseLynxchanUrlHandler(
    url = baseUrl,
    mediaHosts = mediaHosts,
    names = arrayOf("Endchan"),
    siteClass = Endchan::class.java
  )

  companion object {
    private const val TAG = "Endchan"
    const val SITE_NAME = "Endchan"
  }
}