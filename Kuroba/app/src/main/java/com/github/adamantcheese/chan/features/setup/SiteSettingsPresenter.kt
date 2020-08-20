package com.github.adamantcheese.chan.features.setup

import android.content.Context
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.interactors.LoadArchiveInfoListUseCase
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.settings.OptionSettingItem
import com.github.adamantcheese.chan.core.settings.Setting
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteSetting
import com.github.adamantcheese.chan.features.archives.ArchivesSettingsController
import com.github.adamantcheese.chan.features.archives.data.ArchiveState
import com.github.adamantcheese.chan.features.archives.data.ArchiveStatus
import com.github.adamantcheese.chan.features.settings.*
import com.github.adamantcheese.chan.features.settings.setting.InputSettingV2
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.adamantcheese.chan.features.settings.setting.ListSettingV2
import com.github.adamantcheese.chan.ui.controller.LoginController
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SiteSettingsPresenter : BasePresenter<SiteSettingsView>() {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var loadArchiveInfoListUseCase: LoadArchiveInfoListUseCase

  override fun onCreate(view: SiteSettingsView) {
    super.onCreate(view)

    Chan.inject(this)
  }

  suspend fun showSiteSettings(context: Context, siteDescriptor: SiteDescriptor): List<SettingsGroup> {
    siteManager.awaitUntilInitialized()

    val site = siteManager.bySiteDescriptor(siteDescriptor)
    if (site == null) {
      withView {
        val message = context.getString(R.string.site_settings_not_site_found, siteDescriptor.siteName)
        showErrorToast(message)
      }

      return emptyList()
    }

    val isSiteActive = siteManager.isSiteActive(siteDescriptor)
    if (!isSiteActive) {
      withView {
        val message = context.getString(R.string.site_settings_site_is_not_active)
        showErrorToast(message)
      }

      return emptyList()
    }

    return withContext(Dispatchers.Default) {
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

    if (site.siteFeature(Site.SiteFeature.THIRD_PARTY_ARCHIVES)) {
      groups += buildThirdPartyArchivesGroup(context, site.siteDescriptor())
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
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = "Additional settings",
          groupIdentifier = SiteSettingsScreen.AdditionalSettingsGroup
        )

        site.settings().forEach { siteSetting ->
          val settingId = SiteSettingsScreen.AdditionalSettingsGroup.getGroupIdentifier().id + "_" + siteSetting.name
          val identifier = SiteSettingsScreen.AdditionalSettingsGroup(settingId)

          when (siteSetting) {
            is SiteSetting.SiteOptionsSetting -> {
              group += ListSettingV2.createBuilder(
                context = context,
                identifier = identifier,
                setting = siteSetting.options as Setting<OptionSettingItem>,
                items = siteSetting.options.items.toList(),
                itemNameMapper = { item -> item.key },
                topDescriptionStringFunc = { siteSetting.name },
                bottomDescriptionStringFunc = { siteSetting.options.get().name }
              )
            }
            is SiteSetting.SiteStringSetting -> {
              group += InputSettingV2.createBuilder(
                context = context,
                identifier = identifier,
                setting = siteSetting.setting,
                inputType = InputSettingV2.InputType.String,
                topDescriptionStringFunc = { siteSetting.name },
                bottomDescriptionStringFunc = { siteSetting.setting.get() }
              )
            }
          }
        }

        return group
      }
    )
  }

  private fun buildThirdPartyArchivesGroup(
    context: Context,
    siteDescriptor: SiteDescriptor
  ): SettingsGroup.SettingsGroupBuilder {
    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = SiteSettingsScreen.ThirdPartyArchivesGroup,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = "Third party archives",
          groupIdentifier = SiteSettingsScreen.ThirdPartyArchivesGroup
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = SiteSettingsScreen.ThirdPartyArchivesGroup.SetupArchives,
          topDescriptionStringFunc = { "Setup third party archives" },
          bottomDescriptionStringFunc = { formatArchivesDescription() ?: "Error" },
          callback = {
            withViewNormal { pushController(ArchivesSettingsController(context)) }
          }
        )

        return group
      }
    )
  }

  private fun buildAuthenticationGroup(
    context: Context,
    site: Site
  ): SettingsGroup.SettingsGroupBuilder {
    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = SiteSettingsScreen.AuthenticationGroup,
      buildFunction = fun(): SettingsGroup {
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

        return group
      }
    )
  }

  private fun buildGeneralGroup(
    context: Context,
    siteDescriptor: SiteDescriptor
  ): SettingsGroup.SettingsGroupBuilder {
    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = SiteSettingsScreen.GeneralGroup,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = "General",
          groupIdentifier = SiteSettingsScreen.GeneralGroup
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = SiteSettingsScreen.GeneralGroup.SetUpBoards,
          topDescriptionStringFunc = { "Set up boards" },
          bottomDescriptionStringFunc = { "${boardManager.activeBoardsCount(siteDescriptor)} board(s) added" },
          callback = {
            // TODO(KurobaEx): open boards setup controller
          }
        )

        return group
      }
    )
  }

  private fun formatArchivesDescription(): String? {
    // TODO: Get rid of runBlocking!!!!
    val archiveInfoList = runBlocking {
      loadArchiveInfoListUseCase.execute(Unit)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error executing LoadArchiveInfoListUseCase", error)
          return@runBlocking null
        }
    } ?: return null

    val enabledArchives = archiveInfoList.count { archiveInfo ->
      return@count archiveInfo.state == ArchiveState.Enabled
    }
    val permanentlyDisabled = archiveInfoList.count { archiveInfo ->
      return@count archiveInfo.state == ArchiveState.PermanentlyDisabled
    }
    val workingArchives = archiveInfoList.count { archiveInfo ->
      return@count archiveInfo.status == ArchiveStatus.Working
        || archiveInfo.status == ArchiveStatus.ExperiencingProblems
    }

    return AndroidUtils.getString(
      R.string.setup_site_setup_archives_description,
      archiveInfoList.size,
      permanentlyDisabled,
      enabledArchives,
      workingArchives
    )
  }

  sealed class SiteSettingsScreen(
    groupIdentifier: GroupIdentifier,
    settingsIdentifier: SettingIdentifier,
    screenIdentifier: ScreenIdentifier = SiteSettingsScreen.getScreenIdentifier()
  ) : IScreen,
    SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

    sealed class GeneralGroup(
      settingsId: String,
      groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
    ) : IGroup,
      SiteSettingsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

      object SetUpBoards : GeneralGroup("set_up_boards")

      companion object : IGroupIdentifier() {
        override fun getScreenIdentifier(): ScreenIdentifier = SiteSettingsScreen.getScreenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("general_group")
      }
    }

    sealed class AuthenticationGroup(
      settingsId: String,
      groupIdentifier: GroupIdentifier = AuthenticationGroup.getGroupIdentifier()
    ) : IGroup,
      SiteSettingsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

      object Login : AuthenticationGroup("login")

      companion object : IGroupIdentifier() {
        override fun getScreenIdentifier(): ScreenIdentifier = SiteSettingsScreen.getScreenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("authentication_group")
      }
    }

    sealed class ThirdPartyArchivesGroup(
      settingsId: String,
      groupIdentifier: GroupIdentifier = AuthenticationGroup.getGroupIdentifier()
    ) : IGroup,
      SiteSettingsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

      object SetupArchives : ThirdPartyArchivesGroup("setup_archives")

      companion object : IGroupIdentifier() {
        override fun getScreenIdentifier(): ScreenIdentifier = SiteSettingsScreen.getScreenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("third_party_archives_group")
      }
    }

    class AdditionalSettingsGroup(
      settingsId: String,
      groupIdentifier: GroupIdentifier = AuthenticationGroup.getGroupIdentifier()
    ) : IGroup,
      SiteSettingsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

      companion object : IGroupIdentifier() {
        override fun getScreenIdentifier(): ScreenIdentifier = SiteSettingsScreen.getScreenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("additional_settings_group")
      }
    }

    companion object : IScreenIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("developer_settings_screen")
    }
  }

  companion object {
    private const val TAG = "SiteSettingsPresenter"
  }

}