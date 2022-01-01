package com.github.k1rakishou.chan.features.setup

import android.content.Context
import com.github.k1rakishou.OptionSettingItem
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.features.login.LoginController
import com.github.k1rakishou.chan.features.settings.BuildOptions
import com.github.k1rakishou.chan.features.settings.GroupIdentifier
import com.github.k1rakishou.chan.features.settings.IGroup
import com.github.k1rakishou.chan.features.settings.IGroupIdentifier
import com.github.k1rakishou.chan.features.settings.IScreen
import com.github.k1rakishou.chan.features.settings.IScreenIdentifier
import com.github.k1rakishou.chan.features.settings.ScreenIdentifier
import com.github.k1rakishou.chan.features.settings.SettingIdentifier
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.features.settings.setting.InputSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SiteSettingsPresenter(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val compositeCatalogManager: CompositeCatalogManager
) : BasePresenter<SiteSettingsView>() {

  suspend fun showSiteSettings(context: Context, siteDescriptor: SiteDescriptor): List<SettingsGroup> {
    return withContext(Dispatchers.Default) {
      siteManager.awaitUntilInitialized()
      boardManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptor(siteDescriptor)
      if (site == null) {
        withView {
          val message = context.getString(R.string.site_settings_not_site_found, siteDescriptor.siteName)
          showErrorToast(message)
        }

        return@withContext emptyList()
      }

      val isSiteActive = siteManager.isSiteActive(siteDescriptor)
      if (!isSiteActive) {
        withView {
          val message = context.getString(R.string.site_settings_site_is_not_active)
          showErrorToast(message)
        }

        return@withContext emptyList()
      }

      val groups = collectGroupBuilders(context, site)
        .map { it.buildFunction.invoke() }

      groups.forEach { settingsGroup -> settingsGroup.rebuildSettings(BuildOptions.Default) }

      return@withContext groups
    }
  }

  private fun collectGroupBuilders(context: Context, site: Site): List<SettingsGroup.SettingsGroupBuilder> {
    val groups = mutableListOf<SettingsGroup.SettingsGroupBuilder>()

    groups += buildGeneralGroup(context, site.siteDescriptor())

    if (site.siteFeature(Site.SiteFeature.LOGIN)) {
      groups += buildAuthenticationGroup(context, site)
    }

    if (site.settings().isNotEmpty()) {
      groups += buildSiteSpecificSettingsGroup(context, site)
    }

    return groups
  }

  private fun buildSiteSpecificSettingsGroup(
    context: Context,
    site: Site
  ): SettingsGroup.SettingsGroupBuilder {
    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = SiteSettingsScreen.AdditionalSettingsGroup,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = "Additional settings",
          groupIdentifier = SiteSettingsScreen.AdditionalSettingsGroup
        )

        val groupId = SiteSettingsScreen.AdditionalSettingsGroup.getGroupIdentifier().id

        site.settings().forEach { siteSetting ->
          val settingId = groupId + "_" + siteSetting.settingTitle
          val identifier = SiteSettingsScreen.AdditionalSettingsGroup(settingId)

          when (siteSetting) {
            is SiteSetting.SiteOptionsSetting -> {
              group += ListSettingV2.createBuilder(
                context = context,
                identifier = identifier,
                setting = siteSetting.options as Setting<OptionSettingItem>,
                items = siteSetting.options.items.toList(),
                itemNameMapper = { item -> item.key },
                topDescriptionStringFunc = { siteSetting.settingTitle },
                bottomDescriptionStringFunc = {
                  buildString {
                    if (siteSetting.settingDescription != null) {
                      appendLine(siteSetting.settingDescription)
                    }

                    appendLine(siteSetting.options.get().name)
                  }
                }
              )
            }
            is SiteSetting.SiteStringSetting -> {
              group += InputSettingV2.createBuilder(
                context = context,
                identifier = identifier,
                setting = siteSetting.setting,
                inputType = DialogFactory.DialogInputType.String,
                topDescriptionStringFunc = { siteSetting.settingTitle },
                bottomDescriptionStringFunc = {
                  buildString {
                    if (siteSetting.settingDescription != null) {
                      appendLine(siteSetting.settingDescription)
                    }

                    val currentSetting = siteSetting.setting.get()
                    if (currentSetting.isNotBlank()) {
                      appendLine(currentSetting)
                    }
                  }
                }
              )
            }
          }
        }

        group
      }
    )
  }

  private fun buildAuthenticationGroup(
    context: Context,
    site: Site
  ): SettingsGroup.SettingsGroupBuilder {
    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = SiteSettingsScreen.AuthenticationGroup,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = "Authentication",
          groupIdentifier = SiteSettingsScreen.AuthenticationGroup
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = SiteSettingsScreen.AuthenticationGroup.Login,
          topDescriptionStringFunc = { "Login" },
          bottomDescriptionStringFunc = {
            if (site.actions().isLoggedIn()) {
              "On"
            } else {
              "Off"
            }
          },
          callback = {
            withViewNormal {
              openControllerWrappedIntoBottomNavAwareController(LoginController(context, site))
            }
          }
        )

        group
      }
    )
  }

  private fun buildGeneralGroup(
    context: Context,
    siteDescriptor: SiteDescriptor
  ): SettingsGroup.SettingsGroupBuilder {
    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = SiteSettingsScreen.GeneralGroup,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = "General",
          groupIdentifier = SiteSettingsScreen.GeneralGroup
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = SiteSettingsScreen.GeneralGroup.SetUpBoards,
          topDescriptionStringFunc = { "Set up boards" },
          bottomDescriptionStringFunc = {
            val isCatalogCompositionSite = siteManager.bySiteDescriptor(siteDescriptor)
              ?.siteFeature(Site.SiteFeature.CATALOG_COMPOSITION) == true

            if (isCatalogCompositionSite) {
              "${compositeCatalogManager.count()} composite catalog(s) created"
            } else {
              "${boardManager.activeBoardsCount(siteDescriptor)} board(s) added"
            }
          },
          callback = {
            val site = siteManager.bySiteDescriptor(siteDescriptor)
            if (site == null) {
              Logger.d(TAG, "Site ${siteDescriptor} does not exist")
              return@createBuilder
            }

            if (site.siteFeature(Site.SiteFeature.CATALOG_COMPOSITION)) {
              withViewNormal { pushController(CompositeCatalogsSetupController(context)) }
            } else {
              withViewNormal { pushController(BoardsSetupController(context, siteDescriptor)) }
            }
          }
        )

        group
      }
    )
  }

  sealed class SiteSettingsScreen(
    groupIdentifier: GroupIdentifier,
    settingsIdentifier: SettingIdentifier,
    screenIdentifier: ScreenIdentifier = screenIdentifier()
  ) : IScreen,
    SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

    sealed class GeneralGroup(
      settingsId: String,
      groupIdentifier: GroupIdentifier = getGroupIdentifier()
    ) : IGroup,
      SiteSettingsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

      object SetUpBoards : GeneralGroup("set_up_boards")

      companion object : IGroupIdentifier() {
        override fun screenIdentifier(): ScreenIdentifier = SiteSettingsScreen.screenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("general_group")
      }
    }

    sealed class AuthenticationGroup(
      settingsId: String,
      groupIdentifier: GroupIdentifier = getGroupIdentifier()
    ) : IGroup,
      SiteSettingsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

      object Login : AuthenticationGroup("login")

      companion object : IGroupIdentifier() {
        override fun screenIdentifier(): ScreenIdentifier = SiteSettingsScreen.screenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("authentication_group")
      }
    }

    class AdditionalSettingsGroup(
      settingsId: String,
      groupIdentifier: GroupIdentifier = AuthenticationGroup.getGroupIdentifier()
    ) : IGroup,
      SiteSettingsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

      companion object : IGroupIdentifier() {
        override fun screenIdentifier(): ScreenIdentifier = SiteSettingsScreen.screenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("additional_settings_group")
      }
    }

    companion object : IScreenIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("developer_settings_screen")
    }
  }

  companion object {
    private const val TAG = "SiteSettingsPresenter"
  }

}