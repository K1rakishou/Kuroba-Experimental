@file:Suppress("RemoveRedundantQualifierName")

package com.github.adamantcheese.chan.features.settings

import java.util.*

inline class Identifier(val id: String)
inline class SettingIdentifier(val id: String)
inline class ScreenIdentifier(val id: String)
inline class GroupIdentifier(val id: String)

interface IScreen
interface IGroup

abstract class IIdentifiable {
  abstract fun getIdentifier(): Identifier

  override fun hashCode(): Int {
    return getIdentifier().hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return getIdentifier().id == (other as? IIdentifiable)?.getIdentifier()?.id
  }

  override fun toString(): String {
    return getIdentifier().id
  }
}

abstract class IScreenIdentifier : IIdentifiable() {
  abstract fun getScreenIdentifier(): ScreenIdentifier

  override fun getIdentifier(): Identifier {
    return Identifier(getScreenIdentifier().id)
  }
}

abstract class IGroupIdentifier : IScreenIdentifier() {
  abstract fun getGroupIdentifier(): GroupIdentifier

  override fun getIdentifier(): Identifier {
    val id = String.format(
      Locale.ENGLISH,
      "%s_%s",
      getScreenIdentifier().id,
      getGroupIdentifier().id
    )

    return Identifier(id)
  }
}

sealed class SettingsIdentifier(
  private val screenIdentifier: ScreenIdentifier,
  private val groupIdentifier: GroupIdentifier,
  private val settingsIdentifier: SettingIdentifier
) : IIdentifiable() {

  override fun getIdentifier(): Identifier {
    val id = String.format(
      Locale.ENGLISH,
      "%s_%s_%s",
      screenIdentifier.id,
      groupIdentifier.id,
      settingsIdentifier.id
    )

    return Identifier(id)
  }

  override fun toString(): String {
    return getIdentifier().id
  }

}

// ======================================================
// ================= MainScreen =========================
// ======================================================

sealed class MainScreen(
  groupIdentifier: GroupIdentifier,
  settingsIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = MainScreen.getScreenIdentifier()
) : IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) :
    IGroup,
    MainScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ThreadWatcher : MainGroup("thread_watcher")
    object SitesSetup : MainGroup("sites_setup")
    object Appearance : MainGroup("appearance")
    object Behavior : MainGroup("behavior")
    object Media : MainGroup("media")
    object ImportExport : MainGroup("import_export")
    object Filters : MainGroup("filters")
    object Experimental : MainGroup("experimental")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = MainScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  sealed class AboutAppGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = AboutAppGroup.getGroupIdentifier()
  ) :
    IGroup,
    MainScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object AppVersion : AboutAppGroup("app_version")
    object Reports : AboutAppGroup("reports")
    object CollectCrashReport : AboutAppGroup("collect_crash_reports")
    object FindAppOnGithub : AboutAppGroup("find_app_on_github")
    object AppLicense : AboutAppGroup("app_license")
    object AppLicenses : AboutAppGroup("app_licenses")
    object DeveloperSettings : AboutAppGroup("developer_settings")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = MainScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("about_app_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("main_screen")
  }
}

// ======================================================
// ================= DeveloperScreen ====================
// ======================================================

sealed class DeveloperScreen(
  groupIdentifier: GroupIdentifier,
  settingsIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
) : IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    DeveloperScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ViewLogs : MainGroup("view_logs")
    object EnableDisableVerboseLogs : MainGroup("enable_disable_verbose_logs")
    object CrashApp : MainGroup("crash_the_app")
    object ClearFileCache : MainGroup("clear_file_cache")
    object ShowDatabaseSummary : MainGroup("show_database_summary")
    object FilterWatchIgnoreReset : MainGroup("filter_watch_ignore_reset")
    object DumpThreadStack : MainGroup("dump_thread_stack")
    object ForceAwakeWakeables : MainGroup("force_awake_wakeables")
    object ResetThreadOpenCounter : MainGroup("reset_thread_open_counter")
    object CrashOnSafeThrow : MainGroup("crash_on_safe_throw")
    object SimulateAppUpdated : MainGroup("simulate_app_updated")
    object SimulateAppNotUpdated : MainGroup("simulate_app_not_updated")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("developer_settings_screen")
  }
}

// ===========================================================
// ================= DatabaseSummaryScreen ===================
// ===========================================================

sealed class DatabaseSummaryScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = DatabaseSummaryScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) :
    IGroup,
    DatabaseSummaryScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ClearInlinedFilesTable : MainGroup("clear_inlined_files_table")
    object ClearLinkExtraInfoTable : MainGroup("clear_link_info_table")
    object ClearSeenPostsTable : MainGroup("clear_seen_posts_table")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DatabaseSummaryScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("database_summary_screen")
  }
}

// =========================================================
// ================= ThreadWatcherScreen ===================
// =========================================================

sealed class ThreadWatcherScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = ThreadWatcherScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    ThreadWatcherScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object EnableThreadWatcher : MainGroup("enable_thread_watcher")
    object ShortPinInfo : MainGroup("short_pin_info")
    object EnableBackgroundThreadWatcher : MainGroup("enable_background_thread_watcher")
    object ThreadWatcherTimeout : MainGroup("thread_watcher_timeout")
    object RemoveWatchedThreadsFromCatalog : MainGroup("remove_watched_threads_from_catalog")
    object WatchLastPageNotify : MainGroup("watch_last_page_notify")
    object WatchNotifyMode : MainGroup("watch_notify_mode")
    object WatchSound : MainGroup("watch_sound")
    object WatchHeadsup : MainGroup("watch_headsup")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("thread_watcher_screen")
  }
}

// ======================================================
// ================= AppearanceScreen ===================
// ======================================================

sealed class AppearanceScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = AppearanceScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ThemeCustomization : MainGroup("theme_customization")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  sealed class LayoutGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object LayoutMode : LayoutGroup("layout_mode")
    object CatalogColumnsCount : LayoutGroup("catalog_columns_count")
    object NeverHideToolbar : LayoutGroup("never_hide_toolbar")
    object EnableReplyFAB : LayoutGroup("enable_reply_fab")
    object MoveInputToBottom : LayoutGroup("move_input_to_bottom")
    object BottomJsCaptcha : LayoutGroup("bottom_js_captcha")
    object NeverShowPages : LayoutGroup("never_show_pages")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("layout_group")
    }
  }

  sealed class PostGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object FontSize : PostGroup("font_size")
    object FontAlternate : PostGroup("font_alternate")
    object ShiftPostFormat : PostGroup("shift_post_format")
    object PostFullDate : PostGroup("post_full_date")
    object PostFileInfo : PostGroup("post_file_info")
    object PostFileName : PostGroup("post_file_name")
    object TextOnly : PostGroup("text_only")
    object RevealTextSpoilers : PostGroup("reveal_text_spoilers")
    object Anonymize : PostGroup("anonymize")
    object ShowAnonymousName : PostGroup("show_anonymous_name")
    object AnonymizeIds : PostGroup("anonymize_ids")
    object AddDubs : PostGroup("add_dubs")
    object ParseYoutubeTitlesAndDuration : PostGroup("parse_youtube_titles_and_duration")
    object ShowYoutubeLinkAlongWithTitleAndDuration : PostGroup("show_youtube_links_along_with_title_and_duration")
    object EnableEmoji : PostGroup("enable_emoji")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("post_group")
    }
  }

  sealed class ImagesGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object HideImages : ImagesGroup("hide_images")
    object RemoveImageSpoilers : ImagesGroup("remove_image_spoilers")
    object RevealImageSpoilers : ImagesGroup("reveal_image_spoilers")
    object HighResCells : ImagesGroup("high_res_cells")
    object ParsePostImageLinks : ImagesGroup("parse_post_image_links")
    object FetchInlinedFileSizes : ImagesGroup("fetch_inlined_file_sizes")
    object TransparencyOn : ImagesGroup("transparency_on")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("images_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("appearance_screen")
  }
}

// ====================================================
// ================= BehaviorScreen ===================
// ====================================================

sealed class BehaviorScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = BehaviorScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class GeneralGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object AutoRefreshThread : GeneralGroup("auto_refresh_thread")
    object ControllerSwipeable : GeneralGroup("controller_swipeable")
    object OpenLinkConfirmation : GeneralGroup("open_link_confirmation")
    object OpenLinkBrowser : GeneralGroup("open_link_browser")
    object ImageViewerGestures : GeneralGroup("image_viewer_gestures")
    object AlwaysOpenDrawer : GeneralGroup("always_open_drawer")
    object CaptchaSetup : GeneralGroup("catpcha_setup")
    object JsCaptchaCookiesEditor : GeneralGroup("js_captcha_cookies_editor")
    object ClearThreadHides : GeneralGroup("clear_thread_hides")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("general_group")
    }
  }

  sealed class RepliesGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object PostPinThread : RepliesGroup("post_pin_thread")
    object PostDefaultName : RepliesGroup("post_default_name")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("replies_group")
    }
  }

  sealed class PostGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object RepliesButtonsBottom : PostGroup("replies_buttons_bottom")
    object VolumeKeysScrolling : PostGroup("volume_keys_scrolling")
    object TapNoReply : PostGroup("tap_no_reply")
    object EnableLongPressURLCopy : PostGroup("enable_long_press_url_copy")
    object ShareUrl : PostGroup("share_url")
    object MarkUnseenPosts : PostGroup("mark_unseen_posts")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("post_group")
    }
  }

  sealed class OtherSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ParseYoutubeAPIKey : OtherSettingsGroup("parse_youtube_api_key")
    object FullUserRotationEnable : OtherSettingsGroup("full_user_rotation")
    object AllowFilePickChooser : OtherSettingsGroup("allow_file_pick_chooser")
    object AllowMediaScannerToScanLocalThreads : OtherSettingsGroup("allow_media_scanner_to_scan_local_threads")
    object ShowCopyApkUpdateDialog : OtherSettingsGroup("show_copy_apk_update_dialog")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("other_settings_group")
    }
  }

  sealed class ProxySettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ProxyEnabled : ProxySettingsGroup("proxy_enabled")
    object ProxyAddress : ProxySettingsGroup("proxy_address")
    object ProxyPort : ProxySettingsGroup("proxy_port")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("proxy_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("behavior_screen")
  }
}

// =================================================
// ================= MediaScreen ===================
// =================================================

sealed class MediaScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = MediaScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MediaSavingGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MediaSavingGroup.getGroupIdentifier()
  ) : IGroup,
    MediaScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object MediaSaveLocation : MediaSavingGroup("media_save_location")
    object ThreadDownloadEnabled : MediaSavingGroup("thread_download_enabled")
    object ThreadSaveLocation : MediaSavingGroup("thread_save_location")
    object SaveBoardFolder : MediaSavingGroup("save_board_folder")
    object SaveThreadFolder : MediaSavingGroup("save_thread_folder")
    object SaveServerFilename : MediaSavingGroup("save_server_file_name")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("media_saving_group")
    }
  }

  sealed class VideoGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MediaSavingGroup.getGroupIdentifier()
  ) : IGroup,
    MediaScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object VideoAutoLoop : VideoGroup("video_auto_loop")
    object VideoDefaultMuted : VideoGroup("video_default_muted")
    object HeadsetDefaultMuted : VideoGroup("headset_default_muted")
    object VideoOpenExternal : VideoGroup("video_open_external")
    object VideoStream : VideoGroup("video_stream")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("video_group")
    }
  }

  sealed class LoadingGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MediaSavingGroup.getGroupIdentifier()
  ) : IGroup,
    MediaScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ImageAutoLoadNetwork : LoadingGroup("image_auto_load_network")
    object VideoAutoLoadNetwork : LoadingGroup("video_auto_load_network")
    object ImageClickPreloadStrategy : LoadingGroup("image_click_preloading_strategy")
    object AutoLoadThreadImages : LoadingGroup("auto_load_thread_images")
    object ShowPrefetchLoadingIndicator : LoadingGroup("show_prefetch_loading_indicator")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("loading_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("media_screen")
  }
}

// ========================================================
// ================= ImportExportScreen ===================
// ========================================================

sealed class ImportExportScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = ImportExportScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainSettingsGroup.getGroupIdentifier()
  ) : IGroup,
    ImportExportScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ExportSetting : MainSettingsGroup("export_settings")
    object ImportSetting : MainSettingsGroup("import_settings")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("import_export_screen")
  }
}


// ================================================================
// ================= ExperimentalSettingsScreen ===================
// ================================================================

sealed class ExperimentalScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = ExperimentalScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainSettingsGroup.getGroupIdentifier()
  ) : IGroup,
    ExperimentalScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ConcurrentDownloadChunkCount : MainSettingsGroup("concurrent_download_chunk_count")
    object GesturesExclusionZonesEditor : MainSettingsGroup("gestures_exclusion_zones_editor")
    object ResetExclusionZones : MainSettingsGroup("reset_exclusion_zones")
    object OkHttpAllowHttp2 : MainSettingsGroup("ok_http_allow_http_2")
    object OkHttpAllowIpv6 : MainSettingsGroup("ok_http_allow_ipv6")

    companion object : IGroupIdentifier() {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("experimental_screen")
  }
}