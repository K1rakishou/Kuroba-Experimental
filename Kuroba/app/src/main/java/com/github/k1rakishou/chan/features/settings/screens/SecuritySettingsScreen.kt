package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.features.proxies.ProxySetupController
import com.github.k1rakishou.chan.features.settings.SecurityScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSetting
import com.github.k1rakishou.chan.features.settings.setting.LinkSetting
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString

class SecuritySettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val proxyStorage: ProxyStorage
) : BaseSettingsScreen(
  context,
  SecurityScreen,
  R.string.settings_screen_security
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(buildMainGroup())
  }

  private fun buildMainGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = SecurityScreen.MainSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_screen_security_main_group),
          groupIdentifier = identifier
        )

        group += LinkSetting.createBuilder(
          context = context,
          identifier = SecurityScreen.MainSettingsGroup.Proxy,
          topDescriptionIdFunc = { R.string.settings_screen_security_proxy },
          bottomDescriptionStringFunc = {
            val proxiesCount = proxyStorage.getCount()
            getString(R.string.settings_screen_security_proxy_description, proxiesCount)
          },
          callback = { navigationController.pushController(ProxySetupController(context)) }
        )

        group += BooleanSetting.createBuilder(
          context = context,
          identifier = SecurityScreen.MainSettingsGroup.ForceHttpsScheme,
          topDescriptionIdFunc = { R.string.settings_screen_security_force_https_urls },
          bottomDescriptionIdFunc = { R.string.settings_screen_security_force_https_urls_description },
          setting = ChanSettings.forceHttpsUrlScheme,
          requiresRestart = true
        )

        group
      }
    )
  }

}