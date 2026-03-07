package com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.prefs.CookieSetting
import okhttp3.HttpUrl

class Chan8Moe : BaseLynxchanSite(
  defaultDomain = "https://8chan.moe"
) {
  override val name: String = SITE_NAME
  override val postingViaFormData: Boolean = true
  override val endpoints by lazy { Chan8MoeEndpoints(this) }
  override val requestModifier by lazy { Chan8MoeRequestModifier(this) }
  override val urlHandler by lazy { Chan8MoeUrlHandler(this, mediaHosts) }

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

  val powToken by lazy { CookieSetting(dependencies.moshi, prefs, "pow_token") }
  val powId by lazy { CookieSetting(dependencies.moshi, prefs, "pow_id") }

  class Chan8MoeUrlHandler(
    site: BaseLynxchanSite,
    mediaHosts: Set<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    site = site,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "8chan.moe"

    const val POW_TOKEN = "POW_TOKEN"
    const val POW_ID = "POW_ID"
  }
}
