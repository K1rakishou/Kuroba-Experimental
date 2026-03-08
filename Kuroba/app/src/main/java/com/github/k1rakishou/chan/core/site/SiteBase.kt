package com.github.k1rakishou.chan.core.site

import androidx.annotation.CallSuper
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.Setting
import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.chan.core.site.settings.SiteBaseSettings
import com.github.k1rakishou.chan.core.site.settings.SiteSettingForUi
import com.github.k1rakishou.chan.core.site.settings.SiteSettingsForUi
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.SecureRandom
import java.util.Random
import javax.inject.Inject

abstract class SiteBase(
  private val defaultDomain: String
) : Site {
  @Inject
  lateinit var injectedSiteDependencies: Lazy<SiteDependencies>

  val appConstants: AppConstants
    get() = injectedSiteDependencies.get().appConstants
  val boardManager: BoardManager
    get() = injectedSiteDependencies.get().boardManager
  val siteManager: SiteManager
    get() = injectedSiteDependencies.get().siteManager
  val proxiedOkHttpClient: ProxiedOkHttpClient
    get() = injectedSiteDependencies.get().proxiedOkHttpClient
  val httpCallManager: HttpCallManager
    get() = injectedSiteDependencies.get().httpCallManager
  val moshi: Moshi
    get() = injectedSiteDependencies.get().moshi
  val imageLoaderDeprecated: ImageLoaderDeprecated
    get() = injectedSiteDependencies.get().imageLoaderDeprecated
  val archivesManager: ArchivesManager
    get() = injectedSiteDependencies.get().archivesManager
  val postFilterManager: PostFilterManager
    get() = injectedSiteDependencies.get().postFilterManager
  val replyManager: ReplyManager
    get() = injectedSiteDependencies.get().replyManager
  val chanThreadManager: ChanThreadManager
    get() = injectedSiteDependencies.get().chanThreadManager

  override val dependencies: SiteDependencies
    get() = injectedSiteDependencies.get()

  private val commonSettings by lazy {
    SiteBaseSettings(
      defaultDomain = defaultDomain,
      prefs = prefs,
      dependencies = dependencies
    )
  }

  val currentDomain by lazy {
    val siteDomain = commonSettings.siteDomainSetting.get()
    if (siteDomain != null) {
      val siteDomainUrl = siteDomain.toHttpUrlOrNull()
      if (siteDomainUrl != null) {
        Logger.d(TAG, "Using domain: \'${siteDomainUrl}\'")
        return@lazy siteDomainUrl
      }
    }

    val defaultDomainUrl = defaultDomain.toHttpUrl()

    Logger.debug(TAG) {
      "Using default domain: \'${defaultDomainUrl}\' since custom domain seems to be incorrect: \'$siteDomain\'"
    }

    return@lazy defaultDomainUrl
  }

  val currentDomainString by lazy { currentDomain.toString().removeSuffix("/") }

  protected val prefs by lazy {
    val sharedPrefs = AppModuleAndroidUtils.getPreferencesForSite(descriptor)
    return@lazy SharedPreferencesSettingProvider(sharedPrefs)
  }

  override val settings: SiteBaseSettings
    get() = commonSettings

  override val settingsForUi: SiteSettingsForUi by lazy {
    val settings = SiteSettingsForUi()

    settings += SiteSettingForUi.SiteOptionsSetting(
      getString(R.string.settings_concurrent_file_downloading_name),
      getString(R.string.settings_concurrent_file_downloading_description),
      "concurrent_file_downloading_chunks",
      commonSettings.concurrentFileDownloadingChunks,
      ChanSettings.ConcurrentFileDownloadingChunks.entries.map { it.name }
    )

    settings += SiteSettingForUi.SiteMapSetting(
      getString(R.string.cloud_flare_cookie_setting_title),
      null,
      commonSettings.cloudFlareClearanceCookieMap
    )

    settings += SiteSettingForUi.SiteStringSetting(
      getString(R.string.site_domain_setting, descriptor.siteName),
      getString(R.string.site_domain_setting_description),
      commonSettings.siteDomainSetting
    )

    settings += SiteSettingForUi.SiteBooleanSetting(
      getString(R.string.site_ignore_reply_cooldowns),
      getString(R.string.site_ignore_reply_cooldowns_description),
      commonSettings.ignoreReplyCooldowns
    )

    return@lazy settings
  }

  @CallSuper
  override suspend fun initialize() {
    Chan.getComponent()
      .inject(this)
  }

  override fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSettingForUi.SiteSettingId): T? {
    return when (settingId) {
      SiteSettingForUi.SiteSettingId.CloudFlareClearanceCookie -> commonSettings.cloudFlareClearanceCookieMap as T
      SiteSettingForUi.SiteSettingId.LastUsedReplyMode -> commonSettings.lastUsedReplyMode as T
      SiteSettingForUi.SiteSettingId.IgnoreReplyCooldowns -> commonSettings.ignoreReplyCooldowns as T
      // 4chan only
      SiteSettingForUi.SiteSettingId.LastUsedCountryFlagPerBoard -> null
      // 2ch.hk only
      SiteSettingForUi.SiteSettingId.DvachUserCodeCookie -> null
      // 2ch.hk only
      SiteSettingForUi.SiteSettingId.DvachAntiSpamCookie -> null
      // 4chan only
      SiteSettingForUi.SiteSettingId.Chan4CaptchaSettings -> null
      SiteSettingForUi.SiteSettingId.Check4chanPostAcknowledged -> null
    }
  }

  companion object {
    private const val TAG = "SiteBase"
    const val BoardRefreshIntervalDays = 30

    val secureRandom: Random = SecureRandom()

    @JvmStatic
    fun containsMediaHostUrl(desiredSiteUrl: HttpUrl, siteMediaUrls: Set<HttpUrl>): Boolean {
      val desiredHost = desiredSiteUrl.host

      for (siteMediaUrl in siteMediaUrls) {
        val siteMediaHost = siteMediaUrl.host

        if (desiredHost == siteMediaHost) {
          return true
        }

        if (desiredHost == "www.$siteMediaHost") {
          return true
        }
      }

      return false
    }
  }
}