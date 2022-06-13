package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.ExperimentalScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2

class ExperimentalSettingsScreen(
  context: Context,
) : BaseSettingsScreen(
  context,
  ExperimentalScreen,
  R.string.settings_experimental_settings
) {
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

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.AutoLoadThreadImages,
          topDescriptionIdFunc = { R.string.setting_auto_load_thread_images },
          bottomDescriptionIdFunc = { R.string.setting_auto_load_thread_images_description },
          setting = ChanSettings.prefetchMedia,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.ShowPrefetchLoadingIndicator,
          topDescriptionIdFunc = { R.string.setting_show_prefetch_loading_indicator_title },
          setting = ChanSettings.showPrefetchLoadingIndicator,
          dependsOnSetting = ChanSettings.prefetchMedia
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.HighResCells,
          topDescriptionIdFunc = { R.string.setting_images_high_res },
          bottomDescriptionIdFunc = { R.string.setting_images_high_res_description },
          setting = ChanSettings.highResCells,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ExperimentalScreen.MainSettingsGroup.ColorizeTextSelectionCursors,
          topDescriptionIdFunc = { R.string.setting_update_colors_for_text_selection_cursor },
          bottomDescriptionIdFunc = { R.string.setting_update_colors_for_text_selection_cursor_description },
          setting = ChanSettings.colorizeTextSelectionCursors,
          requiresRestart = true
        )

        group
      }
    )
  }

}