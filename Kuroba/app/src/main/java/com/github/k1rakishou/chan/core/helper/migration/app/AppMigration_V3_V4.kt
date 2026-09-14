package com.github.k1rakishou.chan.core.helper.migration.app

import android.content.Context
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.settings.KurobaCookieSetting
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch

@Suppress("ClassNaming")
class AppMigration_V3_V4 : ApplicationMigration {
  override val version: Int
    get() = 4
  override val changes: String
    get() = "4chan email verification cookie has been merged into 4chan posting cookie."

  override fun perform(context: Context) {
    val siteManager = appDependencies().siteManager

    val countDownLatch = CountDownLatch(1)
    siteManager.runWhenInitialized {
      countDownLatch.countDown()
    }
    countDownLatch.await()

    val chan4Sites = mutableListOf<Chan4>()
    siteManager.viewSitesOrdered { _, site ->
      if (site is Chan4) {
        chan4Sites += site
      }

      return@viewSitesOrdered true
    }

    chan4Sites.forEach { chan4 -> migrateEmailVerificationCookie(chan4) }
  }

  @Suppress("DEPRECATION")
  private fun migrateEmailVerificationCookie(chan4: Chan4) {
    val chan4Settings = chan4.chan4Settings
    val emailVerificationCookieKey = KurobaSettingKey.Site.Chan4.EmailVerificationCookie(chan4.descriptor.siteName)

    val emailVerificationCookieSetting = KurobaCookieSetting(
      database = chan4.dependencies.settingsDatabase,
      kurobaSettingInfo = chan4Settings,
      moshi = chan4.dependencies.moshi,
      key = emailVerificationCookieKey
    )

    val emailVerificationCookie = emailVerificationCookieSetting.readBlocking()
    if (emailVerificationCookie != null && !emailVerificationCookie.expired(System.currentTimeMillis())) {
      // The email verification cookie is the one that was being sent with posts when it existed and was not expired
      Logger.debug(TAG) {
        "Moving email verification cookie into posting cookie for ${chan4.descriptor}, " +
          "emailVerificationCookie: ${emailVerificationCookie}"
      }

      chan4Settings.postingCookie.writeBlocking(emailVerificationCookie)
      chan4Settings.emailVerified.writeBlocking(true)
    } else {
      Logger.debug(TAG) { "No valid email verification cookie for ${chan4.descriptor}, skipping." }
    }

    runBlocking { chan4.dependencies.settingsDatabase.settingDao.deleteByKey(emailVerificationCookieKey.raw) }
  }

  companion object {
    private const val TAG = "AppMigration_V3_V4"
  }
}
