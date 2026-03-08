package com.github.k1rakishou.chan.core.site.settings

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.LongSetting
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting

class SiteBaseSettings(
  defaultDomain: String,
  prefs: SharedPreferencesSettingProvider,
  dependencies: SiteDependencies
) {
  val siteDomainSetting by lazy {
    StringSetting(prefs, "site_domain", defaultDomain)
  }

  val concurrentFileDownloadingChunks by lazy {
    OptionsSetting(
      prefs,
      "concurrent_download_chunk_count",
      ChanSettings.ConcurrentFileDownloadingChunks::class.java,
      ChanSettings.ConcurrentFileDownloadingChunks.Two
    )
  }

  val cloudFlareClearanceCookieMap by lazy {
    MapSetting(
      moshi = dependencies.moshi,
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
  }

  val lastUsedReplyMode by lazy {
    OptionsSetting(
      prefs,
      "last_used_reply_mode",
      ReplyMode::class.java,
      ReplyMode.Unknown
    )
  }

  val ignoreReplyCooldowns by lazy {
    BooleanSetting(prefs, "ignore_reply_cooldowns", false)
  }

  val lastSiteBoardsRefreshTime by lazy {
    LongSetting(prefs, "last_site_boards_refresh_time", 0)
  }

  companion object {
    private const val TAG = "SiteCommonSettings"
  }
}