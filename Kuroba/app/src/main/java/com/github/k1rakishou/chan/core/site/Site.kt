package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.site.SiteConfiguration.CatalogFeature
import com.github.k1rakishou.chan.core.site.SiteConfiguration.SiteFeature
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

interface Site {
  val enabled: Boolean
  val name: String
  val descriptor: SiteDescriptor
  val urlHandler: SiteUrlHandler
  val endpoints: SiteEndpoints
  val requestModifier: SiteRequestModifier<Site>
  val api: SiteApi
  val actions: SiteActions
  val configuration: SiteConfiguration
  val settings: List<SiteSetting>

  suspend fun initialize()

  fun hasSiteFeature(siteFeature: SiteFeature): Boolean
  fun hasCatalogFeature(catalogFeature: CatalogFeature): Boolean = false

  fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T?
}