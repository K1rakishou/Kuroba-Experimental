package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.k1rakishou.chan.features.settings.ExperimentalScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.screens.delegate.ExclusionZoneSettingsDelegate
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.common.AndroidUtils.isAndroid10

class ExperimentalSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val exclusionZonesHolder: Android10GesturesExclusionZonesHolder,
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val dialogFactory: DialogFactory
) : BaseSettingsScreen(
  context,
  ExperimentalScreen,
  R.string.settings_experimental_settings
) {
  private val exclusionZoneSettingsDelegate by lazy {
    ExclusionZoneSettingsDelegate(
      context,
      navigationController,
      exclusionZonesHolder,
      globalWindowInsetsManager,
      dialogFactory
    )
  }

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup()
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = ExperimentalScreen.MainSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.experimental_settings_group),
          groupIdentifier = identifier
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

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.OkHttpUseDnsOverHttps,
          topDescriptionIdFunc = { R.string.setting_allow_okhttp_use_dns_over_https },
          setting = ChanSettings.okHttpUseDnsOverHttps,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.CloudflareForcePreload,
          topDescriptionIdFunc = { R.string.setting_cloudflare_preloading_dialog_title },
          bottomDescriptionIdFunc = { R.string.setting_cloudflare_preloading_dialog_description },
          setting = ChanSettings.cloudflareForcePreload
        )

        group
      }
    )
  }

}