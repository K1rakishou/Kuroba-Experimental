package com.github.k1rakishou.chan.core.site

import androidx.annotation.CallSuper
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
import com.github.k1rakishou.chan.core.site.settings.SiteCommonSettings
import com.github.k1rakishou.chan.core.site.settings.SiteSetting
import com.github.k1rakishou.chan.core.site.settings.SiteSettingsForUi
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
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
  val kurobaSettings: KurobaSettings
    get() = injectedSiteDependencies.get().kurobaSettings

  override val dependencies: SiteDependencies
    get() = injectedSiteDependencies.get()

  val currentDomain by lazy {
    val siteDomain = commonSettings.siteDomainSetting.readBlocking()
    if (siteDomain.isNotNullNorBlank()) {
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

  override val commonSettings by lazy {
    SiteCommonSettings(
      siteDescriptor = descriptor,
      defaultDomain = defaultDomain,
      dependencies = dependencies
    )
  }

  override val settingsForUi: SiteSettingsForUi by lazy {
    val settings = SiteSettingsForUi()

    settings += SiteSetting.SiteOptionsSetting(
      settingName = getString(R.string.settings_concurrent_file_downloading_name),
      settingDescription = getString(R.string.settings_concurrent_file_downloading_description),
      groupId = "concurrent_file_downloading_chunks",
      setting = commonSettings.concurrentFileDownloadingChunks
    )

    settings += SiteSetting.SiteMapSetting(
      settingName = getString(R.string.cloud_flare_cookie_setting_title),
      settingDescription = null,
      setting = commonSettings.cloudFlareClearanceCookieMap
    )

    settings += SiteSetting.SiteStringSetting(
      settingName = getString(R.string.site_domain_setting, descriptor.siteName),
      settingDescription = getString(R.string.site_domain_setting_description),
      setting = commonSettings.siteDomainSetting,
      requiresRestart = true
    )

    settings += SiteSetting.SiteBooleanSetting(
      settingName = getString(R.string.site_ignore_reply_cooldowns),
      settingDescription = getString(R.string.site_ignore_reply_cooldowns_description),
      setting = commonSettings.ignoreReplyCooldowns
    )

    return@lazy settings
  }

  @CallSuper
  override suspend fun initialize() {
    Chan.getComponent()
      .inject(this)
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