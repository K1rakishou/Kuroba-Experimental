package com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.prefs.CookieSetting
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Chan8Moe : LynxchanSite() {
  override val name: String = SITE_NAME
  override val defaultDomain: HttpUrl = DEFAULT_DOMAIN
  override val postingViaFormData: Boolean = true
  override val endpoints by lazy { Chan8MoeEndpoints(this) }
  override val requestModifier by lazy { Chan8MoeRequestModifier(this, appConstants) as SiteRequestModifier<Site> }
  override val urlHandler by lazy { Chan8MoeUrlHandler(domainUrl, mediaHosts) }

  override val settings: List<SiteSetting> by lazy {
    val settings = mutableListOf<SiteSetting>()
    settings.addAll(super.settings)

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

    return@lazy settings
  }

  val powToken by lazy { CookieSetting(moshiLazy, prefs, "pow_token") }
  val powId by lazy { CookieSetting(moshiLazy, prefs, "pow_id") }

  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", defaultDomain.toString())
  }

  class Chan8MoeUrlHandler(
    baseUrl: HttpUrl,
    mediaHosts: Array<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    url = baseUrl,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "8chan.moe"

    const val POW_TOKEN = "POW_TOKEN"
    const val POW_ID = "POW_ID"

    private val DEFAULT_DOMAIN = "https://8chan.moe".toHttpUrl()
  }
}
