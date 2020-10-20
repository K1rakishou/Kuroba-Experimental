package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ProxyStorage
import com.github.k1rakishou.chan.features.proxies.ProxySetupController
import com.github.k1rakishou.chan.features.settings.SecurityScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AndroidUtils.getString

class SecuritySettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val proxyStorage: ProxyStorage
  ) : BaseSettingsScreen(
  context,
  SecurityScreen,
  R.string.settings_screen_security
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(buildMainGroup())
  }

  private fun buildMainGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = SecurityScreen.MainSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_screen_security_proxy_group),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = SecurityScreen.MainSettingsGroup.Proxy,
          topDescriptionIdFunc = { R.string.settings_screen_security_proxy },
          bottomDescriptionStringFunc = {
            val proxiesCount = proxyStorage.getCount()
            getString(R.string.settings_screen_security_proxy_description, proxiesCount)
          },
          callback = { navigationController.pushController(ProxySetupController(context)) }
        )

        return group
      }
    )
  }

}