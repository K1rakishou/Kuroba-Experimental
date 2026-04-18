package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.setup.site.setup.SitesSetupController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.CatalogOrThreadSearchMode
import com.github.k1rakishou.v2.settings.AbstractKurobaSetting

class BehaviourSettingsScreenBuilder(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val postHideManager: PostHideManager,
  private val appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
) : SettingsScreenBuilder {

  override suspend fun build(context: Context, settingActions: SettingActions, settingsScreen: SettingsScreen) {
    with(settingsScreen) {
      buildMainSettingsGroup(context, settingActions)
      buildReplySettingsGroup()
      buildPostSettingsGroup()
      buildOtherSettingsGroup()
    }
  }

  private suspend fun SettingsScreen.buildMainSettingsGroup(context: Context, settingActions: SettingActions) {
    addGroup(
      key = "general",
      title = appResources.string(R.string.settings_group_general)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_auto_refresh_thread) },
          setting = kurobaSettings.application.autoRefreshThread
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_show_thread_page) },
          setting = kurobaSettings.application.showThreadPage
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_controller_swipeable) },
          description = { appResources.string(R.string.setting_controller_swipeable_description) },
          setting = kurobaSettings.application.controllerSwipeable,
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_view_thread_controller_swipeable) },
          description = { appResources.string(R.string.setting_view_thread_controller_swipeable_description) },
          setting = kurobaSettings.application.viewThreadControllerSwipeable,
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_open_link_confirmation) },
          setting = kurobaSettings.application.openLinkConfirmation
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "CaptchaSetup",
          title = { appResources.string(R.string.setting_captcha_setup) },
          description = { appResources.string(R.string.setting_captcha_setup_description) },
          callback = { settingActions.pushController(SitesSetupController(context)) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ClearPostHides",
          title = { appResources.string(R.string.setting_clear_post_hides) },
          callback = {
            postHideManager.clearAllPostHides()
            settingActions.showToast(appResources.string(R.string.setting_cleared_post_hides))
            appSettingsUpdateAppRefreshHelper.settingsUpdated()
          }
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildReplySettingsGroup() {
    addGroup(
      key = "replies",
      title = appResources.string(R.string.settings_group_reply)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_post_pin) },
          setting = kurobaSettings.application.postPinThread
        )
      )

      addSetting(
        SettingUiElement.Input(
          title = { appResources.string(R.string.setting_post_default_name) },
          setting = kurobaSettings.application.postDefaultName,
          dialogInputType = DialogFactory.DialogInputType.String
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildPostSettingsGroup() {
    addGroup(
      key = "post",
      title = appResources.string(R.string.settings_group_post)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_volume_key_scrolling) },
          setting = kurobaSettings.application.volumeKeysScrolling
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_tappable_post_title) },
          description = { appResources.string(R.string.setting_tappable_post_title_description) },
          setting = kurobaSettings.application.tapNoReply,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_mark_unseen_posts_title) },
          description = { appResources.string(R.string.setting_mark_unseen_posts_description) },
          setting = kurobaSettings.application.markUnseenPosts,
          requiresPostListRefresh = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_mark_seen_threads_title) },
          description = { appResources.string(R.string.setting_mark_seen_threads_description) },
          setting = kurobaSettings.application.markSeenThreads,
          requiresPostListRefresh = true
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildOtherSettingsGroup() {
    addGroup(
      key = "other",
      title = appResources.string(R.string.setting_other_settings_group)
    ) {
      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_catalog_search_mode_title) },
          items = CatalogOrThreadSearchMode.entries,
          selectionType = SettingUiElement.SelectionType.Multiple("catalog_search_mode"),
          itemNameMapper = { item ->
            when (item) {
              CatalogOrThreadSearchMode.Filter -> "${item.name} (Posts are filtered out)"
              CatalogOrThreadSearchMode.Highlight -> "${item.name} (Posts are highlighted)"
            }
          },
          setting = kurobaSettings.application.catalogSearchMode
            as AbstractKurobaSetting<CatalogOrThreadSearchMode, CatalogOrThreadSearchMode>
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_thread_search_mode_title) },
          items = CatalogOrThreadSearchMode.entries,
          selectionType = SettingUiElement.SelectionType.Multiple("thread_search_mode"),
          itemNameMapper = { item ->
            when (item) {
              CatalogOrThreadSearchMode.Filter -> "${item.name} (Posts are filtered out)"
              CatalogOrThreadSearchMode.Highlight -> "${item.name} (Posts are highlighted)"
            }
          },
          setting = kurobaSettings.application.threadSearchMode
            as AbstractKurobaSetting<CatalogOrThreadSearchMode, CatalogOrThreadSearchMode>
        )
      )
    }
  }
}