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
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.LongSetting
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import com.squareup.moshi.Moshi
import dagger.Lazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.SecureRandom
import java.util.Random
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

abstract class SiteBase(
  private val defaultDomain: String
) : Site, CoroutineScope {
  private val job = SupervisorJob()

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

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName(this::class.java.simpleName)

  override val dependencies: SiteDependencies
    get() = injectedSiteDependencies.get()

  protected val prefs by lazy {
    val sharedPrefs = AppModuleAndroidUtils.getPreferencesForSite(descriptor)
    return@lazy SharedPreferencesSettingProvider(sharedPrefs)
  }

  private val siteDomainSetting by lazy {
    StringSetting(prefs, "site_domain", defaultDomain)
  }

  val currentDomain by lazy {
    val siteDomain = siteDomainSetting.get()
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

  lateinit var concurrentFileDownloadingChunks: OptionsSetting<ChanSettings.ConcurrentFileDownloadingChunks>
  lateinit var cloudFlareClearanceCookieMap: MapSetting
  lateinit var lastUsedReplyMode: OptionsSetting<ReplyMode>
  lateinit var ignoreReplyCooldowns: BooleanSetting
  lateinit var lastSiteBoardsRefreshTime: LongSetting

  override val settings: List<SiteSetting> by lazy {
    val settings = mutableListOf<SiteSetting>()

    settings += SiteSetting.SiteOptionsSetting(
      getString(R.string.settings_concurrent_file_downloading_name),
      getString(R.string.settings_concurrent_file_downloading_description),
      "concurrent_file_downloading_chunks",
      concurrentFileDownloadingChunks,
      ChanSettings.ConcurrentFileDownloadingChunks.entries.map { it.name }
    )

    settings += SiteSetting.SiteMapSetting(
      getString(R.string.cloud_flare_cookie_setting_title),
      null,
      cloudFlareClearanceCookieMap
    )

    settings += SiteSetting.SiteStringSetting(
      getString(R.string.site_domain_setting, descriptor.siteName),
      getString(R.string.site_domain_setting_description),
      siteDomainSetting
    )

    settings += SiteSetting.SiteBooleanSetting(
      getString(R.string.site_ignore_reply_cooldowns),
      getString(R.string.site_ignore_reply_cooldowns_description),
      ignoreReplyCooldowns
    )

    return@lazy settings
  }

  @CallSuper
  override suspend fun initialize() {
    Chan.getComponent()
      .inject(this)

    concurrentFileDownloadingChunks = OptionsSetting(
      prefs,
      "concurrent_download_chunk_count",
      ChanSettings.ConcurrentFileDownloadingChunks::class.java,
      ChanSettings.ConcurrentFileDownloadingChunks.Two
    )

    cloudFlareClearanceCookieMap = MapSetting(
      moshi = moshi,
      mapperFrom = { mapSettingEntry ->
        return@MapSetting MapSetting.KeyValue(
          key = mapSettingEntry.key,
          value = mapSettingEntry.value
        )
      },
      mapperTo = { keyValue ->
        return@MapSetting MapSetting.MapSettingEntry(
          key = keyValue.key,
          value = keyValue.value
        )
      },
      settingProvider = prefs,
      key = "cloud_flare_clearance_cookie_map",
      def = emptyMap()
    )

    lastUsedReplyMode = OptionsSetting(
      prefs,
      "last_used_reply_mode",
      ReplyMode::class.java,
      ReplyMode.Unknown
    )

    ignoreReplyCooldowns = BooleanSetting(prefs, "ignore_reply_cooldowns", false)
    lastSiteBoardsRefreshTime = LongSetting(prefs, "last_site_boards_refresh_time", 0)
  }

  override fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T? {
    return when (settingId) {
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie -> cloudFlareClearanceCookieMap as T
      SiteSetting.SiteSettingId.LastUsedReplyMode -> lastUsedReplyMode as T
      SiteSetting.SiteSettingId.IgnoreReplyCooldowns -> ignoreReplyCooldowns as T
      // 4chan only
      SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard -> null
      // 2ch.hk only
      SiteSetting.SiteSettingId.DvachUserCodeCookie -> null
      // 2ch.hk only
      SiteSetting.SiteSettingId.DvachAntiSpamCookie -> null
      // 4chan only
      SiteSetting.SiteSettingId.Chan4CaptchaSettings -> null
      SiteSetting.SiteSettingId.Check4chanPostAcknowledged -> null
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