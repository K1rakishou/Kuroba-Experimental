package com.github.k1rakishou.chan.core.helper.migration

import android.content.Context
import com.github.k1rakishou.chan.core.site.SiteRegistry
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.MapSetting
import java.util.concurrent.CountDownLatch

class AppMigration_V2_V3 : ApplicationMigration {
  override val version: Int
    get() = 3
  override val changes: String?
    get() = """
      CloudFlare cookie has been reset for all sites due to it's internal structure changing while the application has been abandoned.
    """.trimIndent()

  override fun perform(context: Context) {
    val siteManager = appDependencies().siteManager

    val countDownLatch = CountDownLatch(1)
    siteManager.runWhenInitialized {
      countDownLatch.countDown()
    }
    countDownLatch.await()

    for (siteDescriptor in SiteRegistry.SITE_CLASSES_MAP.keys) {
      val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
      if (site == null || site.isSynthetic) {
        Logger.debug(TAG) { "Skipping ${siteDescriptor} site." }
        continue
      }

      val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<MapSetting>(
        SiteSetting.SiteSettingId.CloudFlareClearanceCookie
      )
      if (cloudFlareClearanceCookieSetting == null) {
        Logger.debug(TAG) { "Site ${siteDescriptor} has no CloudFlare cookie, skipping." }
        continue
      }

      Logger.debug(TAG) { "Clearing CloudFlare cookie for ${siteDescriptor}." }
      cloudFlareClearanceCookieSetting.clear(sync = true)
    }
  }

  companion object {
    private const val TAG = "AppMigration_V2_V3"
  }
}