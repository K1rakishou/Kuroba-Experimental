package com.github.k1rakishou.chan.core.site.settings

import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaInitialSettingsState
import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.parameters.ConcurrentFileDownloadingChunks
import com.github.k1rakishou.v2.parameters.ReplyMode
import com.github.k1rakishou.v2.settings.KurobaBooleanSetting
import com.github.k1rakishou.v2.settings.KurobaEnumSetting
import com.github.k1rakishou.v2.settings.KurobaLongSetting
import com.github.k1rakishou.v2.settings.KurobaMapSetting
import com.github.k1rakishou.v2.settings.KurobaStringSetting

class SiteCommonSettings(
  private val siteDescriptor: SiteDescriptor,
  defaultDomain: String,
  dependencies: SiteDependencies
) : KurobaSettingInfo {
  override val backupable: Boolean = true
  override val initialSettingsState: KurobaInitialSettingsState = dependencies.kurobaSettings.initialSettingsState

  val siteDomainSetting by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.SiteDomainSetting(siteDescriptor.siteName),
      default = defaultDomain
    )
  }

  val concurrentFileDownloadingChunks by lazy {
    KurobaEnumSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      clazz = ConcurrentFileDownloadingChunks::class.java,
      key = KurobaSettingKey.Site.ConcurrentFileDownloadingChunks(siteDescriptor.siteName),
      default = ConcurrentFileDownloadingChunks.Two
    )
  }

  val cloudFlareClearanceCookieMap by lazy {
    KurobaMapSetting<String, String>(
      moshi = dependencies.moshi,
      kurobaSettingInfo = this,
      mapperFrom = { mapSettingEntry ->
        return@KurobaMapSetting KurobaMapSetting.KeyValue(
          key = mapSettingEntry.key,
          value = mapSettingEntry.value
        )
      },
      mapperTo = { keyValue ->
        return@KurobaMapSetting KurobaMapSetting.MapSettingEntry(
          key = keyValue.key,
          value = keyValue.value
        )
      },
      database = dependencies.settingsDatabase,
      key = KurobaSettingKey.Site.CloudFlareClearanceCookieMap(siteDescriptor.siteName),
      default = emptyMap()
    )
  }

  val lastUsedReplyMode by lazy {
    KurobaEnumSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      clazz = ReplyMode::class.java,
      key = KurobaSettingKey.Site.LastUsedReplyMode(siteDescriptor.siteName),
      default = ReplyMode.Unknown
    )
  }

  val ignoreReplyCooldowns by lazy {
    KurobaBooleanSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.IgnoreReplyCooldowns(siteDescriptor.siteName),
      default = false
    )
  }

  val lastSiteBoardsRefreshTime by lazy {
    KurobaLongSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.LastSiteBoardsRefreshTime(siteDescriptor.siteName),
      default = 0
    )
  }
}