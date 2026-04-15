package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.settings.SiteSetting
import com.github.k1rakishou.chan.features.login.LoginController
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.setup.boards.composing.CompositeCatalogsSetupController
import com.github.k1rakishou.chan.features.setup.boards.reorder.BoardsReorderController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class SiteSettingsScreenBuilder(
  private val appResources: AppResources,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val compositeCatalogManager: CompositeCatalogManager
) {
  suspend fun build(
    context: Context,
    site: Site,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    val siteDescriptor = site.descriptor

    with(settingsScreen) {
      buildGeneralSettingsGroup(context, siteDescriptor, settingActions)

      if (site.hasSiteFeature(SiteConfiguration.SiteFeature.Login)) {
        buildAuthenticationGroup(context, site, settingActions)
      }

      if (site.settingsForUi.isNotEmpty()) {
        buildSiteSpecificSettingsGroup(site)
      }
    }
  }

  private suspend fun SettingsScreen.buildGeneralSettingsGroup(
    context: Context,
    siteDescriptor: SiteDescriptor,
    settingActions: SettingActions
  ) {
    addGroup(
      key = "general_${siteDescriptor.siteName}",
      title = "General settings"
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "setup_boards",
          title = { "Set up boards" },
          description = {
            val isCatalogCompositionSite = siteManager.bySiteDescriptorAndActive(siteDescriptor)
              ?.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition) == true

            buildString {
              if (isCatalogCompositionSite) {
                appendLine("${compositeCatalogManager.count()} composite catalog(s) created")
              } else {
                appendLine("${boardManager.activeBoardsCount(siteDescriptor)} board(s) added")
              }
            }
          },
          callback = {
            val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
            if (site == null) {
              Logger.d(TAG, "Site ${siteDescriptor} does not exist")
              return@Link
            }

            if (site.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition)) {
              settingActions.pushController(CompositeCatalogsSetupController(context))
            } else {
              settingActions.pushController(BoardsReorderController(context, siteDescriptor))
            }

            return@Link
          }
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildSiteSpecificSettingsGroup(
    site: Site
  ) {
    addGroup(
      key = "site_specific",
      title = "Site specific settings"
    ) {
      site.settingsForUi.forEach { siteSetting ->
        when (siteSetting) {
          is SiteSetting.SiteMapSetting -> {
            siteSetting.setting.read().entries.forEach { mapEntry ->
              val mapEntryKey = mapEntry.key

              addSetting(
                SettingUiElement.Map(
                  composeKey = "${siteSetting.setting.key}_${mapEntryKey}",
                  title = { "[${mapEntryKey}] ${siteSetting.title}" },
                  description = { siteSetting.description },
                  currentValue = { siteSetting.setting.get(mapEntryKey) },
                  mapEntryKey = mapEntryKey,
                  setting = siteSetting.setting,
                  requiresAppRestart = siteSetting.requiresRestart
                )
              )
            }
          }

          is SiteSetting.SiteOptionsSetting -> {
            addSetting(
              SettingUiElement.EnumItems(
                title = { siteSetting.title },
                description = { siteSetting.description },
                setting = siteSetting.setting,
                selectionType = if (siteSetting.groupId != null) {
                  SettingUiElement.SelectionType.Multiple(siteSetting.groupId)
                } else {
                  SettingUiElement.SelectionType.Single
                },
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }

          is SiteSetting.SiteStringSetting -> {
            addSetting(
              SettingUiElement.Input(
                title = { siteSetting.title },
                description = { siteSetting.description },
                setting = siteSetting.setting,
                dialogInputType = DialogFactory.DialogInputType.String,
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }

          is SiteSetting.SiteBooleanSetting -> {
            addSetting(
              SettingUiElement.Bool(
                title = { siteSetting.title },
                description = { siteSetting.description },
                setting = siteSetting.setting,
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }

          is SiteSetting.SiteCookieSetting -> {
            addSetting(
              SettingUiElement.Cookie(
                setting = siteSetting.setting,
                title = { siteSetting.title },
                description = { cookieSettingDescription(siteSetting) },
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }
        }
      }
    }
  }

  private suspend fun SettingsScreen.buildAuthenticationGroup(
    context: Context,
    site: Site,
    siteActions: SettingActions
  ) {
    addGroup(
      key = "authentication",
      title = "Authentication"
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "login",
          title = { "Login" },
          description = {
            buildString {
              if (site.actions.isLoggedIn()) {
                appendLine("On")
              } else {
                appendLine("Off")
              }
            }
          },
          callback = {
            siteActions.pushController(LoginController(context, site))
          }
        )
      )
    }
  }

  private suspend fun cookieSettingDescription(siteSetting: SiteSetting.SiteCookieSetting): String {
    return buildString {
      if (siteSetting.description != null) {
        appendLine(siteSetting.description)
      }

      val kurobaCookie = siteSetting.setting.read()
      if (kurobaCookie == null) {
        return@buildString
      }

      appendLine()

      val value = kurobaCookie.value
      if (value.isNotNullNorBlank()) {
        appendLine("Value: ${value}")
      }

      when (kurobaCookie.expiration) {
        KurobaCookie.Expiration.Never -> {
          appendLine(appResources.string(R.string.cookie_captcha_input_controller_expires_never))
        }

        KurobaCookie.Expiration.Session -> {
          appendLine(appResources.string(R.string.cookie_captcha_input_controller_expires_end_of_session))
        }

        is KurobaCookie.Expiration.Time -> {
          val expirationDateFormatted = kurobaCookie.expirationTimeFormatted()
          if (expirationDateFormatted.isNotNullNorBlank()) {
            appendLine(
              appResources.string(
                R.string.cookie_captcha_input_controller_expires_at,
                expirationDateFormatted
              )
            )
          }
        }
      }

      val path = kurobaCookie.path
      if (path.isNotNullNorBlank()) {
        appendLine("Path: ${path}")
      }
    }
  }

  companion object {
    private const val TAG = "SiteSettingsScreenBuilder"
  }
}