package com.github.k1rakishou.chan.core.site

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
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import dagger.Lazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import okhttp3.HttpUrl
import java.security.SecureRandom
import java.util.Random
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

abstract class SiteBase : Site, CoroutineScope {
  private val job = SupervisorJob()

  @Inject
  lateinit var gson: Gson
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var boardManagerLazy: Lazy<BoardManager>
  @Inject
  lateinit var siteManagerLazy: Lazy<SiteManager>
  @Inject
  lateinit var proxiedOkHttpClientLazy: Lazy<ProxiedOkHttpClient>
  @Inject
  lateinit var httpCallManagerLazy: Lazy<HttpCallManager>
  @Inject
  lateinit var moshiLazy: Lazy<Moshi>
  @Inject
  lateinit var imageLoaderDeprecatedLazy: Lazy<ImageLoaderDeprecated>
  @Inject
  lateinit var archivesManagerLazy: Lazy<ArchivesManager>
  @Inject
  lateinit var postFilterManagerLazy: Lazy<PostFilterManager>
  @Inject
  lateinit var replyManagerLazy: Lazy<ReplyManager>
  @Inject
  lateinit var boardFlagInfoRepositoryLazy: Lazy<BoardFlagInfoRepository>
  @Inject
  lateinit var simpleCommentParserLazy: Lazy<SimpleCommentParser>
  @Inject
  lateinit var chanThreadManagerLazy: Lazy<ChanThreadManager>

  val boardManager: BoardManager
    get() = boardManagerLazy.get()
  val siteManager: SiteManager
    get() = siteManagerLazy.get()
  val proxiedOkHttpClient: ProxiedOkHttpClient
    get() = proxiedOkHttpClientLazy.get()
  val httpCallManager: HttpCallManager
    get() = httpCallManagerLazy.get()
  val moshi: Moshi
    get() = moshiLazy.get()
  val imageLoaderDeprecated: ImageLoaderDeprecated
    get() = imageLoaderDeprecatedLazy.get()
  val archivesManager: ArchivesManager
    get() = archivesManagerLazy.get()
  val postFilterManager: PostFilterManager
    get() = postFilterManagerLazy.get()
  val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  val boardFlagInfoRepository: BoardFlagInfoRepository
    get() = boardFlagInfoRepositoryLazy.get()
  val simpleCommentParser: SimpleCommentParser
    get() = simpleCommentParserLazy.get()
  val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("SiteBase")

  protected val prefs by lazy {
    val sharedPrefs = AppModuleAndroidUtils.getPreferencesForSite(siteDescriptor())
    return@lazy SharedPreferencesSettingProvider(sharedPrefs)
  }

  open val siteDomainSetting: StringSetting? = null

  lateinit var concurrentFileDownloadingChunks: OptionsSetting<ChanSettings.ConcurrentFileDownloadingChunks>
  lateinit var cloudFlareClearanceCookieMap: MapSetting
  lateinit var lastUsedReplyMode: OptionsSetting<ReplyMode>
  lateinit var ignoreReplyCooldowns: BooleanSetting

  private var initialized = false

  override fun initialize() {
    if (initialized) {
      throw IllegalStateException("Already initialized")
    }

    Chan.getComponent()
      .inject(this)

    initialized = true
  }

  override fun postInitialize() {
    concurrentFileDownloadingChunks = OptionsSetting(
      prefs,
      "concurrent_download_chunk_count",
      ChanSettings.ConcurrentFileDownloadingChunks::class.java,
      ChanSettings.ConcurrentFileDownloadingChunks.Two
    )

    cloudFlareClearanceCookieMap = MapSetting(
      _moshi = moshiLazy,
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
  }

  override suspend fun loadBoardInfo(): Flow<SiteBoards> {
    if (!enabled()) {
      return flowOf(SiteBoards.Result.Success(siteDescriptor(), emptyList()))
    }

    if (!boardsType().canList) {
      return flowOf(SiteBoards.Result.Success(siteDescriptor(), emptyList()))
    }

    return actions().boards()
      .onEach { siteBoards ->
        when (siteBoards) {
          is SiteBoards.Progress -> {
            // no-op
          }
          is SiteBoards.Result.Error -> {
            Logger.error(TAG, siteBoards.error) { "loadBoardInfo(${siteDescriptor()}) error" }
          }
          is SiteBoards.Result.Success -> {
            boardManager.createOrUpdateBoards(siteBoards.boards)

            Logger.debug(TAG) {
              "Got the boards for site ${siteBoards.siteDescriptor.siteName}, " +
                "boards count: ${siteBoards.boards.size}"
            }
          }
        }
      }
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

  override fun settings(): List<SiteSetting> {
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

    if (siteDomainSetting != null) {
      val siteName = siteDescriptor().siteName

      settings += SiteSetting.SiteStringSetting(
        getString(R.string.site_domain_setting, siteName),
        getString(R.string.site_domain_setting_description),
        siteDomainSetting!!
      )
    }

    settings += SiteSetting.SiteBooleanSetting(
      getString(R.string.site_ignore_reply_cooldowns),
      getString(R.string.site_ignore_reply_cooldowns_description),
      ignoreReplyCooldowns
    )

    return settings
  }

  companion object {
    private const val TAG = "SiteBase"
    val secureRandom: Random = SecureRandom()

    @JvmStatic
    fun containsMediaHostUrl(desiredSiteUrl: HttpUrl, siteMediaUrls: Array<HttpUrl>): Boolean {
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