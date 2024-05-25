package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.Setting
import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.comment.HtmlParserPool
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

abstract class SiteBase : Site, CoroutineScope {
  private val job = SupervisorJob()

  @Inject
  lateinit var proxiedOkHttpClientLazy: Lazy<RealProxiedOkHttpClient>
  @Inject
  lateinit var appConstantsLazy: Lazy<AppConstants>
  @Inject
  lateinit var boardManagerLazy: Lazy<BoardManager>
  @Inject
  lateinit var httpCallManagerLazy: Lazy<HttpCallManager>
  @Inject
  lateinit var moshiLazy: Lazy<Moshi>
  @Inject
  lateinit var gsonLazy: Lazy<Gson>
  @Inject
  lateinit var siteManagerLazy: Lazy<SiteManager>
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
  lateinit var staticHtmlColorRepositoryLazy: Lazy<StaticHtmlColorRepository>
  @Inject
  lateinit var htmlParserPoolLazy: Lazy<HtmlParserPool>

  protected val proxiedOkHttpClient: RealProxiedOkHttpClient
    get() = proxiedOkHttpClientLazy.get()
  protected val appConstants: AppConstants
    get() = appConstantsLazy.get()
  protected val boardManager: BoardManager
    get() = boardManagerLazy.get()
  protected val httpCallManager: HttpCallManager
    get() = httpCallManagerLazy.get()
  protected val moshi: Moshi
    get() = moshiLazy.get()
  protected val gson: Gson
    get() = gsonLazy.get()
  protected val siteManager: SiteManager
    get() = siteManagerLazy.get()
  protected val imageLoaderDeprecated: ImageLoaderDeprecated
    get() = imageLoaderDeprecatedLazy.get()
  protected val archivesManager: ArchivesManager
    get() = archivesManagerLazy.get()
  protected val postFilterManager: PostFilterManager
    get() = postFilterManagerLazy.get()
  protected val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  protected val boardFlagInfoRepository: BoardFlagInfoRepository
    get() = boardFlagInfoRepositoryLazy.get()
  protected val simpleCommentParser: SimpleCommentParser
    get() = simpleCommentParserLazy.get()
  protected val staticHtmlColorRepository: StaticHtmlColorRepository
    get() = staticHtmlColorRepositoryLazy.get()
  protected val htmlParserPool: HtmlParserPool
    get() = htmlParserPoolLazy.get()

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
      moshiLazy = moshiLazy,
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

  override fun loadBoardInfo(callback: ((ModularResult<SiteBoards>) -> Unit)?): Job? {
    if (!enabled()) {
      callback?.invoke(ModularResult.value(SiteBoards(siteDescriptor(), emptyList())))
      return null
    }

    if (!boardsType().canList) {
      callback?.invoke(ModularResult.value(SiteBoards(siteDescriptor(), emptyList())))
      return null
    }

    return launch(Dispatchers.IO) {
      val result = ModularResult.Try {
        boardManager.awaitUntilInitialized()
        Logger.d(TAG, "Requesting boards for site ${name()}")

        val readerResponse = actions().boards()

        when (readerResponse) {
          is ModularResult.Error -> {
            Logger.e(TAG, "Couldn't get site boards", readerResponse.error)
          }
          is ModularResult.Value -> {
            val siteBoards = readerResponse.value

            boardManager.createOrUpdateBoards(siteBoards.boards)

            Logger.d(TAG, "Got the boards for site ${siteBoards.siteDescriptor.siteName}, " +
              "boards count = ${siteBoards.boards.size}")
          }
        }

        return@Try readerResponse.unwrap()
      }

      if (callback != null) {
        callback.invoke(result)
        return@launch
      }

      if (result is ModularResult.Error) {
        Logger.e(TAG, "loadBoardInfo error", result.error)
        throw result.error
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