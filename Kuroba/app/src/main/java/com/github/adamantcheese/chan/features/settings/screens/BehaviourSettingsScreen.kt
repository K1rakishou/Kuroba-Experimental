package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import android.widget.Toast
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.features.settings.BehaviorScreen
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.setting.BooleanSettingV2
import com.github.adamantcheese.chan.features.settings.setting.InputSettingV2
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.adamantcheese.chan.ui.controller.SitesSetupController
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.ui.controller.settings.captcha.JsCaptchaCookiesEditorController
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus

class BehaviourSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val databaseManager: DatabaseManager
) : BaseSettingsScreen(
  context,
  BehaviorScreen,
  R.string.setting_other_settings_group
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup(),
      buildReplySettingsGroup(),
      buildPostSettingsGroup(),
      buildOtherSettingsGroup(),
      buildProxySettingsGroup()
    )
  }

  private fun buildProxySettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = BehaviorScreen.ProxySettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_proxy),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.ProxySettingsGroup.ProxyEnabled,
          topDescriptionIdFunc = { R.string.setting_proxy_enabled },
          setting = ChanSettings.proxyEnabled,
          requiresRestart = true
        )

        group += InputSettingV2.createBuilder<String>(
          context = context,
          identifier = BehaviorScreen.ProxySettingsGroup.ProxyAddress,
          topDescriptionIdFunc = { R.string.setting_proxy_address },
          bottomDescriptionStringFunc = { ChanSettings.proxyAddress.get() },
          setting = ChanSettings.proxyAddress,
          dependsOnSetting = ChanSettings.proxyEnabled,
          requiresRestart = true,
          inputType = InputSettingV2.InputType.String
        )

        group += InputSettingV2.createBuilder<Int>(
          context = context,
          identifier = BehaviorScreen.ProxySettingsGroup.ProxyPort,
          topDescriptionIdFunc = { R.string.setting_proxy_port },
          bottomDescriptionStringFunc = { ChanSettings.proxyPort.get().toString() },
          setting = ChanSettings.proxyPort,
          dependsOnSetting = ChanSettings.proxyEnabled,
          requiresRestart = true,
          inputType = InputSettingV2.InputType.Integer
        )

        return group
      }
    )
  }

  private fun buildOtherSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = BehaviorScreen.OtherSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.setting_other_settings_group),
          groupIdentifier = identifier
        )

        group += InputSettingV2.createBuilder<String>(
          context = context,
          identifier = BehaviorScreen.OtherSettingsGroup.ParseYoutubeAPIKey,
          topDescriptionIdFunc = { R.string.setting_youtube_api_key },
          bottomDescriptionStringFunc = { ChanSettings.parseYoutubeAPIKey.get() },
          setting = ChanSettings.parseYoutubeAPIKey,
          inputType = InputSettingV2.InputType.String
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.OtherSettingsGroup.FullUserRotationEnable,
          topDescriptionIdFunc = { R.string.setting_full_screen_rotation },
          setting = ChanSettings.fullUserRotationEnable,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.OtherSettingsGroup.AllowFilePickChooser,
          topDescriptionIdFunc = { R.string.setting_allow_alternate_file_pickers },
          bottomDescriptionIdFunc = { R.string.setting_allow_alternate_file_pickers_description },
          setting = ChanSettings.allowFilePickChooser
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.OtherSettingsGroup.AllowMediaScannerToScanLocalThreads,
          topDescriptionIdFunc = { R.string.settings_allow_media_scanner_scan_local_threads_title },
          bottomDescriptionIdFunc = { R.string.settings_allow_media_scanner_scan_local_threads_description },
          setting = ChanSettings.allowMediaScannerToScanLocalThreads
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.OtherSettingsGroup.ShowCopyApkUpdateDialog,
          topDescriptionIdFunc = { R.string.settings_show_copy_apk_dialog_title },
          bottomDescriptionIdFunc = { R.string.settings_show_copy_apk_dialog_message },
          setting = ChanSettings.showCopyApkUpdateDialog
        )

        return group
      }
    )
  }

  private fun buildPostSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = BehaviorScreen.PostGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_post),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.PostGroup.RepliesButtonsBottom,
          topDescriptionIdFunc = { R.string.setting_buttons_bottom },
          setting = ChanSettings.repliesButtonsBottom
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.PostGroup.VolumeKeysScrolling,
          topDescriptionIdFunc = { R.string.setting_volume_key_scrolling },
          setting = ChanSettings.volumeKeysScrolling
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.PostGroup.TapNoReply,
          topDescriptionIdFunc = { R.string.setting_tap_no_rely },
          setting = ChanSettings.tapNoReply
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.PostGroup.EnableLongPressURLCopy,
          topDescriptionIdFunc = { R.string.settings_image_long_url },
          bottomDescriptionIdFunc = { R.string.settings_image_long_url_description },
          setting = ChanSettings.enableLongPressURLCopy
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.PostGroup.ShareUrl,
          topDescriptionIdFunc = { R.string.setting_share_url },
          bottomDescriptionIdFunc = { R.string.setting_share_url_description },
          setting = ChanSettings.shareUrl
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.PostGroup.MarkUnseenPosts,
          topDescriptionIdFunc = { R.string.setting_mark_unseen_posts_title },
          bottomDescriptionIdFunc = { R.string.setting_mark_unseen_posts_duration },
          setting = ChanSettings.markUnseenPosts,
          requiresUiRefresh = true
        )

        return group
      }
    )
  }

  private fun buildReplySettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = BehaviorScreen.RepliesGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_reply),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.RepliesGroup.PostPinThread,
          topDescriptionIdFunc = { R.string.setting_post_pin },
          setting = ChanSettings.postPinThread
        )

        group += InputSettingV2.createBuilder<String>(
          context = context,
          identifier = BehaviorScreen.RepliesGroup.PostDefaultName,
          topDescriptionIdFunc = { R.string.setting_post_default_name },
          bottomDescriptionStringFunc = { ChanSettings.postDefaultName.get() },
          setting = ChanSettings.postDefaultName,
          inputType = InputSettingV2.InputType.String
        )

        return group
      }
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = BehaviorScreen.GeneralGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_general),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.AutoRefreshThread,
          topDescriptionIdFunc = { R.string.setting_auto_refresh_thread },
          setting = ChanSettings.autoRefreshThread
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.ControllerSwipeable,
          topDescriptionIdFunc = { R.string.setting_controller_swipeable },
          setting = ChanSettings.controllerSwipeable,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.OpenLinkConfirmation,
          topDescriptionIdFunc = { R.string.setting_open_link_confirmation },
          setting = ChanSettings.openLinkConfirmation
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.OpenLinkBrowser,
          topDescriptionIdFunc = { R.string.setting_open_link_browser },
          setting = ChanSettings.openLinkBrowser
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.ImageViewerGestures,
          topDescriptionIdFunc = { R.string.setting_image_viewer_gestures },
          bottomDescriptionIdFunc = { R.string.setting_image_viewer_gestures_description },
          setting = ChanSettings.imageViewerGestures
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.AlwaysOpenDrawer,
          topDescriptionIdFunc = { R.string.settings_always_open_drawer },
          setting = ChanSettings.alwaysOpenDrawer
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.CaptchaSetup,
          topDescriptionIdFunc = { R.string.settings_captcha_setup },
          bottomDescriptionIdFunc = { R.string.settings_captcha_setup_description },
          callback = { navigationController.pushController(SitesSetupController(context)) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.JsCaptchaCookiesEditor,
          topDescriptionIdFunc = { R.string.settings_js_captcha_cookies_title },
          bottomDescriptionIdFunc = { R.string.settings_js_captcha_cookies_description },
          callback = { navigationController.pushController(JsCaptchaCookiesEditorController(context)) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = BehaviorScreen.GeneralGroup.ClearThreadHides,
          topDescriptionIdFunc = { R.string.setting_clear_thread_hides },
          callback = {
            databaseManager.runTask(databaseManager.databaseHideManager.clearAllThreadHides())
            AndroidUtils.showToast(context, R.string.setting_cleared_thread_hides, Toast.LENGTH_LONG)
            postToEventBus(RefreshUIMessage("clearhides"))
          }
        )

        return group
      }
    )
  }
}