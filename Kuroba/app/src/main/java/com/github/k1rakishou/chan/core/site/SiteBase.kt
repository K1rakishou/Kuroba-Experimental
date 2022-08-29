/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.Setting
import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.BooleanSetting
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
  lateinit var proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>
    protected set
  @Inject
  lateinit var appConstants: AppConstants
    protected set
  @Inject
  lateinit var boardManager: BoardManager
    protected set

  @Inject
  protected lateinit var httpCallManager: Lazy<HttpCallManager>
  @Inject
  protected lateinit var moshi: Lazy<Moshi>
  @Inject
  protected lateinit var siteManager: SiteManager
  @Inject
  protected lateinit var imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  protected lateinit var archivesManager: ArchivesManager

  @Inject
  protected lateinit var postFilterManager: Lazy<PostFilterManager>
  @Inject
  protected lateinit var replyManager: Lazy<ReplyManager>
  @Inject
  protected lateinit var gson: Gson
  @Inject
  protected lateinit var boardFlagInfoRepository: Lazy<BoardFlagInfoRepository>
  @Inject
  protected lateinit var simpleCommentParser: Lazy<SimpleCommentParser>

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("SiteBase")

  protected val prefs by lazy {
    val sharedPrefs = AppModuleAndroidUtils.getPreferencesForSite(siteDescriptor())
    return@lazy SharedPreferencesSettingProvider(sharedPrefs)
  }

  open val siteDomainSetting: StringSetting? = null

  lateinit var concurrentFileDownloadingChunks: OptionsSetting<ChanSettings.ConcurrentFileDownloadingChunks>
  lateinit var cloudFlareClearanceCookie: StringSetting
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

    cloudFlareClearanceCookie = StringSetting(
      prefs,
      "cloud_flare_clearance_cookie",
      ""
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

  override fun board(code: String): ChanBoard? {
    val boardDescriptor = BoardDescriptor.create(siteDescriptor(), code)
    return boardManager.byBoardDescriptor(boardDescriptor)
  }

  override fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T? {
    return when (settingId) {
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie -> cloudFlareClearanceCookie as T
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
    }
  }

  override fun settings(): List<SiteSetting> {
    val settings = mutableListOf<SiteSetting>()

    settings += SiteSetting.SiteOptionsSetting(
      getString(R.string.settings_concurrent_file_downloading_name),
      getString(R.string.settings_concurrent_file_downloading_description),
      "concurrent_file_downloading_chunks",
      concurrentFileDownloadingChunks,
      ChanSettings.ConcurrentFileDownloadingChunks.values().map { it.name }
    )

    settings += SiteSetting.SiteStringSetting(
      getString(R.string.cloud_flare_cookie_setting_title),
      getString(R.string.cloud_flare_cookie_setting_description),
      cloudFlareClearanceCookie
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

  override suspend fun createBoard(boardName: String, boardCode: String): ModularResult<ChanBoard?> {
    val existing = board(boardCode)
    if (existing != null) {
      return ModularResult.value(existing)
    }

    val boardDescriptor = BoardDescriptor.create(siteDescriptor(), boardCode)
    val board = ChanBoard.create(boardDescriptor, boardName)

    val created = boardManager.createOrUpdateBoards(listOf(board))
    if (!created) {
      return ModularResult.value(null)
    }

    return ModularResult.value(board)
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