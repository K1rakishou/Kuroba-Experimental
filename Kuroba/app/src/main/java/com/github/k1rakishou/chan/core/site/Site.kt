package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.site.SiteConfiguration.CatalogFeature
import com.github.k1rakishou.chan.core.site.SiteConfiguration.SiteFeature
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.chan.core.site.settings.SiteCommonSettings
import com.github.k1rakishou.chan.core.site.settings.SiteSettingsForUi
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
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
  val settings: SiteSpecificSettings?
  val commonSettings: SiteCommonSettings
  val settingsForUi: SiteSettingsForUi
  val dependencies: SiteDependencies

  suspend fun initialize()

  fun hasSiteFeature(siteFeature: SiteFeature): Boolean
  fun hasCatalogFeature(catalogFeature: CatalogFeature): Boolean = false

  fun <T : SiteSpecificSettings> siteSettingsOrNull(clazz: Class<T>): T? {
    return if (clazz.isInstance(settings)) clazz.cast(settings) else null
  }

  fun <T : SiteSpecificSettings> requireSiteSettings(clazz: Class<T>): T {
    return if (clazz.isInstance(settings)) {
      clazz.cast(settings)
    } else {
      error("Cannot cast settings to ${clazz::class.java.name}")
    }
  }
}