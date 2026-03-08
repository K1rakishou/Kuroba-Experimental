package com.github.k1rakishou.chan.features.setup.site.settings

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
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.settings.SiteSettingForUi
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
import com.github.k1rakishou.chan.features.settings.setting.BooleanSetting
import com.github.k1rakishou.chan.features.settings.setting.CookieSetting
import com.github.k1rakishou.chan.features.settings.setting.InputSetting
import com.github.k1rakishou.chan.features.settings.setting.LinkSetting
import com.github.k1rakishou.chan.features.settings.setting.ListSetting
import com.github.k1rakishou.chan.features.settings.setting.MapSetting
import com.github.k1rakishou.chan.features.setup.boards.composing.CompositeCatalogsSetupController
import com.github.k1rakishou.chan.features.setup.boards.reorder.BoardsReorderController
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.isNotNullNorBlank
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

      val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
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

    groups += buildGeneralGroup(context, site.descriptor)

    if (site.hasSiteFeature(SiteConfiguration.SiteFeature.Login)) {
      groups += buildAuthenticationGroup(context, site)
    }

    if (site.settingsForUi.isNotEmpty()) {
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

        site.settingsForUi.forEach { siteSetting ->
          val settingId = groupId + "_" + siteSetting.settingTitle
          val identifier = SiteSettingsScreen.AdditionalSettingsGroup(settingId)

          when (siteSetting) {
            is SiteSettingForUi.SiteMapSetting -> {
              siteSetting.setting.get().forEach { mapEntry ->
                val mapSettingId = groupId + "_" + siteSetting.settingTitle + mapEntry.key
                val mapSettingIdentifier = SiteSettingsScreen.AdditionalSettingsGroup(mapSettingId)

                group += MapSetting.createBuilder(
                  context = context,
                  identifier = mapSettingIdentifier,
                  mapKey = mapEntry.key,
                  setting = siteSetting.setting,
                  inputType = DialogFactory.DialogInputType.String,
                  topDescriptionStringFunc = { siteSetting.settingTitle + " (${mapEntry.key})" },
                  bottomDescriptionStringFunc = {
                    buildString {
                      if (siteSetting.settingDescription != null) {
                        appendLine(siteSetting.settingDescription)
                      }

                      val currentSetting = siteSetting.setting.get(mapEntry.key)
                      if (currentSetting.isNotNullNorBlank()) {
                        appendLine(currentSetting)
                      }
                    }
                  }
                )
              }
            }
            is SiteSettingForUi.SiteOptionsSetting -> {
              group += ListSetting.createBuilder(
                context = context,
                identifier = identifier,
                setting = siteSetting.options as Setting<OptionSettingItem>,
                items = siteSetting.options.items.toList(),
                groupId = siteSetting.groupId,
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
            is SiteSettingForUi.SiteStringSetting -> {
              group += InputSetting.createBuilder(
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
            is SiteSettingForUi.SiteBooleanSetting -> {
              val bottomDescriptionStringFunc: (suspend () -> String)? = if (siteSetting.settingDescription != null) {
                { siteSetting.settingDescription }
              } else {
                null
              }

              group += BooleanSetting.createBuilder(
                context = context,
                identifier = identifier,
                setting = siteSetting.setting,
                dependsOnSetting = null,
                topDescriptionStringFunc = { siteSetting.settingTitle },
                bottomDescriptionStringFunc = bottomDescriptionStringFunc
              )
            }
            is SiteSettingForUi.SiteCookieSetting -> {
              group += CookieSetting.createBuilder(
                context = context,
                identifier = identifier,
                setting = siteSetting.setting,
                inputType = DialogFactory.DialogInputType.String,
                topDescriptionStringFunc = { siteSetting.settingTitle },
                bottomDescriptionStringFunc = {
                  return@createBuilder buildString {
                    if (siteSetting.settingDescription != null) {
                      appendLine(siteSetting.settingDescription)
                    }

                    val kurobaCookie = siteSetting.setting.get()
                    if (kurobaCookie == null) {
                      return@buildString
                    }

                    appendLine()

                    val value = kurobaCookie.value
                    if (value.isNotNullNorBlank()) {
                      appendLine("Value: ${value}")
                    }

                    when (kurobaCookie.expiration) {
                      KurobaCookie.Expiration.Never -> appendLine("Expires: never")
                      KurobaCookie.Expiration.Session -> appendLine("Expires: end of session")
                      is KurobaCookie.Expiration.Time -> {
                        val expirationDateFormatted = kurobaCookie.expirationTimeFormatted()
                        if (expirationDateFormatted.isNotNullNorBlank()) {
                          appendLine("Expires: ${expirationDateFormatted}")
                        }
                      }
                    }

                    val path = kurobaCookie.path
                    if (path.isNotNullNorBlank()) {
                      appendLine("Path: ${path}")
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

        group += LinkSetting.createBuilder(
          context = context,
          identifier = SiteSettingsScreen.AuthenticationGroup.Login,
          topDescriptionStringFunc = { "Login" },
          bottomDescriptionStringFunc = {
            if (site.actions.isLoggedIn()) {
              "On"
            } else {
              "Off"
            }
          },
          callback = {
            withViewNormal {
              pushController(LoginController(context, site))
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

        group += LinkSetting.createBuilder(
          context = context,
          identifier = SiteSettingsScreen.GeneralGroup.SetUpBoards,
          topDescriptionStringFunc = { "Set up boards" },
          bottomDescriptionStringFunc = {
            val isCatalogCompositionSite = siteManager.bySiteDescriptorAndActive(siteDescriptor)
              ?.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition) == true

            if (isCatalogCompositionSite) {
              "${compositeCatalogManager.count()} composite catalog(s) created"
            } else {
              "${boardManager.activeBoardsCount(siteDescriptor)} board(s) added"
            }
          },
          callback = {
            val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
            if (site == null) {
              Logger.d(TAG, "Site ${siteDescriptor} does not exist")
              return@createBuilder
            }

            if (site.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition)) {
              withViewNormal { pushController(CompositeCatalogsSetupController(context)) }
            } else {
              withViewNormal { pushController(BoardsReorderController(context, siteDescriptor)) }
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