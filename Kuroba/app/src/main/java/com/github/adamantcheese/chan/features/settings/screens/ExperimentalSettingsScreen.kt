package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.NavigationController
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.adamantcheese.chan.features.settings.ExperimentalScreen
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.screens.delegate.ExclusionZoneSettingsDelegate
import com.github.adamantcheese.chan.features.settings.setting.BooleanSettingV2
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.adamantcheese.chan.features.settings.setting.ListSettingV2
import com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast

class ExperimentalSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val exclusionZonesHolder: Android10GesturesExclusionZonesHolder
) : BaseSettingsScreen(
  context,
  ExperimentalScreen,
  R.string.settings_experimental_settings_title
) {
  private val exclusionZoneSettingsDelegate by lazy {
    ExclusionZoneSettingsDelegate(
      context,
      navigationController,
      exclusionZonesHolder
    )
  }

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup()
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = ExperimentalScreen.MainSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.experimental_settings_group),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<ChanSettings.ConcurrentFileDownloadingChunks>(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.ConcurrentDownloadChunkCount,
          topDescriptionIdFunc = { R.string.settings_concurrent_file_downloading_name },
          bottomDescriptionStringFunc = { itemName ->
            context.getString(R.string.settings_concurrent_file_downloading_description) + "\n\n" + itemName
          },
          items = ChanSettings.ConcurrentFileDownloadingChunks.values().toList(),
          itemNameMapper = { concurrentDownloadChunkCount -> concurrentDownloadChunkCount.name },
          setting = ChanSettings.concurrentDownloadChunkCount,
          requiresRestart = true
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.GesturesExclusionZonesEditor,
          topDescriptionIdFunc = { R.string.setting_exclusion_zones_editor },
          bottomDescriptionIdFunc = { R.string.setting_exclusion_zones_editor_description },
          callback = { exclusionZoneSettingsDelegate.showZonesDialog() },
          isEnabledFunc = { isAndroid10() },
          requiresUiRefresh = true
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.ResetExclusionZones,
          topDescriptionIdFunc = { R.string.setting_exclusion_zones_reset_zones },
          bottomDescriptionIdFunc = { R.string.setting_exclusion_zones_reset_zones_description },
          callback = {
            exclusionZonesHolder.resetZones()
            showToast(context, R.string.done)
          },
          isEnabledFunc = { isAndroid10() },
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.OkHttpAllowHttp2,
          topDescriptionIdFunc = { R.string.setting_allow_okhttp_http2 },
          bottomDescriptionIdFunc = { R.string.setting_allow_okhttp_http2_ipv6_description },
          setting = ChanSettings.okHttpAllowHttp2,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.OkHttpAllowIpv6,
          topDescriptionIdFunc = { R.string.setting_allow_okhttp_ipv6 },
          bottomDescriptionIdFunc = { R.string.setting_allow_okhttp_http2_ipv6_description },
          setting = ChanSettings.okHttpAllowIpv6,
          requiresRestart = true
        )

        return group
      }
    )
  }

}