package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.features.proxies.ProxySetupController
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.helper.AppResources

class SecuritySettingsScreenBuilder(
  private val appResources: AppResources,
  private val proxyStorage: ProxyStorage
) : SettingsScreenBuilder {

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildMainGroup(context, settingActions)
    }
  }

  private suspend fun SettingsScreen.buildMainGroup(
    context: Context,
    settingActions: SettingActions
  ) {
    addGroup(
      key = "main",
      title = appResources.string(R.string.settings_screen_security_main_group),
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "Proxy",
          title = { appResources.string(R.string.settings_screen_security_proxy) },
          description = {
            val proxiesCount = proxyStorage.getCount()
            return@Link appResources.string(R.string.settings_screen_security_proxy_description, proxiesCount)
          },
          callback = { settingActions.pushController(ProxySetupController(context)) }
        )
      )
    }
  }
}