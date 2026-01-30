package com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanEndpoints
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.prefs.CookieSetting
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Chan8Moe : LynxchanSite() {
  private val siteIconLazy by lazy {
    SiteIcon.fromFavicon(imageLoaderDeprecatedLazy, "${domainString}/favicon.ico".toHttpUrl())
  }
  private val siteRequestModifier by lazy {
    Chan8MoeRequestModifier(this, appConstants)
  }

  private val chan8MoeEndpoints = lazy { Chan8MoeEndpoints(this) }
  private val mediaHostsLazy = lazy { arrayOf(domainUrl.value) }
  private val siteUrlHandler = lazy { Chan8MoeUrlHandler(domainUrl.value, mediaHostsLazy.value) }

  val powToken by lazy { CookieSetting(moshiLazy, prefs, "pow_token") }
  val powId by lazy { CookieSetting(moshiLazy, prefs, "pow_id") }

  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", defaultDomain.toString())
  }

  override val defaultDomain: HttpUrl
    get() = DEFAULT_DOMAIN
  override val siteName: String
    get() = SITE_NAME
  override val siteIcon: SiteIcon
    get() = siteIconLazy
  override val urlHandler: Lazy<BaseLynxchanUrlHandler>
    get() = siteUrlHandler
  override val endpoints: Lazy<LynxchanEndpoints>
    get() = chan8MoeEndpoints
  override val postingViaFormData: Boolean
    get() = true

  override fun requestModifier(): SiteRequestModifier<Site> = siteRequestModifier as SiteRequestModifier<Site>

  override fun settings(): List<SiteSetting> {
    val settings = mutableListOf<SiteSetting>()
    settings.addAll(super.settings())

    settings += SiteSetting.SiteCookieSetting(
      settingName = "powToken",
      settingDescription = getString(R.string.chan8moe_pow_token),
      setting = powToken
    )
    settings += SiteSetting.SiteCookieSetting(
      settingName = "powId",
      settingDescription = getString(R.string.chan8moe_pow_id),
      setting = powId
    )

    return settings
  }

  class Chan8MoeUrlHandler(baseUrl: HttpUrl, mediaHosts: Array<HttpUrl>) : BaseLynxchanUrlHandler(
    url = baseUrl,
    mediaHosts = mediaHosts,
    names = arrayOf("8chan.moe"),
    siteClass = Chan8Moe::class.java
  )

  companion object {
    private const val TAG = "8chan.moe"
    const val SITE_NAME = "8chan.moe"

    const val POW_TOKEN = "POW_TOKEN"
    const val POW_ID = "POW_ID"

    val DESCRIPTOR = SiteDescriptor.create(SITE_NAME)

    private val DEFAULT_DOMAIN = "https://8chan.moe".toHttpUrl()
  }

}
