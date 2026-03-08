package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.site.SiteConfiguration.CatalogFeature
import com.github.k1rakishou.chan.core.site.SiteConfiguration.SiteFeature
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.chan.core.site.settings.SiteBaseSettings
import com.github.k1rakishou.chan.core.site.settings.SiteSettingForUi
import com.github.k1rakishou.chan.core.site.settings.SiteSettingsForUi
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

interface Site {
  val enabled: Boolean
  val name: String
  val descriptor: SiteDescriptor
  val urlHandler: SiteUrlHandler
  val endpoints: SiteEndpoints
  val requestModifier: SiteRequestModifier
  val api: SiteApi
  val actions: SiteActions
  val configuration: SiteConfiguration
  val postParser: PostParser?
  val settings: SiteBaseSettings
  val settingsForUi: SiteSettingsForUi
  val dependencies: SiteDependencies

  suspend fun initialize()

  fun hasSiteFeature(siteFeature: SiteFeature): Boolean
  fun hasCatalogFeature(catalogFeature: CatalogFeature): Boolean = false

  fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSettingForUi.SiteSettingId): T?
}