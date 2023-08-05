@file:Suppress("RemoveRedundantQualifierName")

package com.github.k1rakishou.chan.features.settings

import java.util.*

@JvmInline
value class Identifier(val id: String)
@JvmInline
value class SettingIdentifier(val id: String)
@JvmInline
value class ScreenIdentifier(val id: String)
@JvmInline
value class GroupIdentifier(val id: String)

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
  abstract fun screenIdentifier(): ScreenIdentifier

  override fun getIdentifier(): Identifier {
    return Identifier(screenIdentifier().id)
  }
}

abstract class IGroupIdentifier : IScreenIdentifier() {
  abstract fun getGroupIdentifier(): GroupIdentifier

  override fun getIdentifier(): Identifier {
    val id = String.format(
      Locale.ENGLISH,
      "%s_%s",
      screenIdentifier().id,
      getGroupIdentifier().id
    )

    return Identifier(id)
  }
}

abstract class SettingsIdentifier(
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
  screenIdentifier: ScreenIdentifier = MainScreen.screenIdentifier()
) : IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) :
    IGroup,
    MainScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ThreadAndFilterWatcher : MainGroup("thread_filter_watcher")
    object SitesSetup : MainGroup("sites_setup")
    object Appearance : MainGroup("appearance")
    object Behavior : MainGroup("behavior")
    object Media : MainGroup("media")
    object ImportExport : MainGroup("import_export")
    object Filters : MainGroup("filters")
    object Security : MainGroup("security")
    object Caching : MainGroup("caching")
    object Plugins : MainGroup("plugins")
    object Experimental : MainGroup("experimental")
    object CaptchaSolvers : MainGroup("captcha_solvers")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = MainScreen.screenIdentifier()
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
    object Changelog : AboutAppGroup("changelog")
    object Reports : AboutAppGroup("reports")
    object FindAppOnGithub : AboutAppGroup("find_app_on_github")
    object TryKurobaExLite : AboutAppGroup("try_kuroba_ex_lite")
    object ReportTrackerLink : AboutAppGroup("report_tracker_link")
    object AppLicense : AboutAppGroup("app_license")
    object DeveloperSettings : AboutAppGroup("developer_settings")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = MainScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("about_app_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("main_screen")
  }
}

// ======================================================
// ================= DeveloperScreen ====================
// ======================================================

sealed class DeveloperScreen(
  groupIdentifier: GroupIdentifier,
  settingsIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = DeveloperScreen.screenIdentifier()
) : IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    DeveloperScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ForceLowRamDevice : MainGroup("force_low_ram_device")
    object CheckUpateApkVersionCode : MainGroup("check_update_apk_version_code")
    object ShowMpvInternalLogs : MainGroup("show_mpv_internal_logs")
    object ViewLogs : MainGroup("view_logs")
    object EnableDisableVerboseLogs : MainGroup("enable_disable_verbose_logs")
    object CrashApp : MainGroup("crash_the_app")
    object ShowDatabaseSummary : MainGroup("show_database_summary")
    object ResetThreadOpenCounter : MainGroup("reset_thread_open_counter")
    object CrashOnSafeThrow : MainGroup("crash_on_safe_throw")
    object SimulateAppUpdated : MainGroup("simulate_app_updated")
    object SimulateAppNotUpdated : MainGroup("simulate_app_not_updated")
    object AutoThemeSwitcher : MainGroup("auto_theme_switcher")
    object FunThingsAreFun : MainGroup("fun_things_are_fun")
    object Force4chanBirthday : MainGroup("force_4chan_birthday")
    object ForceHalloween : MainGroup("force_halloween")
    object ForceChristmas : MainGroup("force_christmas")
    object ForceNewYear : MainGroup("force_new_year")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = DeveloperScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("developer_settings_screen")
  }
}

// ===========================================================
// ================= DatabaseSummaryScreen ===================
// ===========================================================

sealed class DatabaseSummaryScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = DatabaseSummaryScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) :
    IGroup,
    DatabaseSummaryScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ClearLinkExtraInfoTable : MainGroup("clear_link_info_table")
    object ClearSeenPostsTable : MainGroup("clear_seen_posts_table")
    object ThreadsTable : MainGroup("threads_table")
    object PostsTable : MainGroup("posts_table")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = DatabaseSummaryScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("database_summary_screen")
  }
}

// =========================================================
// ================= WatcherScreen ===================
// =========================================================

sealed class WatcherScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = WatcherScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class ThreadWatcherGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = ThreadWatcherGroup.getGroupIdentifier()
  ) : IGroup,
    WatcherScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object EnableThreadWatcher : ThreadWatcherGroup("enable_thread_watcher")
    object EnableBackgroundThreadWatcher : ThreadWatcherGroup("enable_background_thread_watcher")
    object ThreadWatcherBackgroundUpdateInterval : ThreadWatcherGroup("thread_watcher_background_update_interval")
    object ThreadWatcherForegroundUpdateInterval : ThreadWatcherGroup("thread_watcher_foreground_update_interval")
    object AdaptiveForegroundWatcherInterval : ThreadWatcherGroup("adaptive_foreground_watcher_interval")
    object ReplyNotifications : ThreadWatcherGroup("reply_notifications")
    object UseSoundForReplyNotifications : ThreadWatcherGroup("use_sound_for_reply_notifications")
    object WatchLastPageNotify : ThreadWatcherGroup("watch_last_page_notify")
    object UseSoundForLastPageNotifications : ThreadWatcherGroup("use_sound_for_last_page_notifications")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = WatcherScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("thread_watcher_group")
    }
  }

  sealed class FilterWatcherGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = FilterWatcherGroup.getGroupIdentifier()
  ) : IGroup,
    WatcherScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object EnableFilterWatcher : FilterWatcherGroup("enable_filter_watcher")
    object FilterWatcherUseFilterPatternForGroup : FilterWatcherGroup("filter_watcher_use_filter_pattern_for_group")
    object FilterWatcherUpdateInterval : FilterWatcherGroup("filter_watcher_update_interval")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = WatcherScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("filter_watcher_group")
    }
  }

  sealed class ThreadDownloaderGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = ThreadDownloaderGroup.getGroupIdentifier()
  ) : IGroup,
    WatcherScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ThreadDownloaderUpdateInterval : ThreadDownloaderGroup("thread_downloader_update_interval")
    object ThreadDownloaderDownloadMediaOnMeteredNetwork : ThreadDownloaderGroup("thread_downloader_download_media_on_metered_network")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = WatcherScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("thread_downloader_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("watcher_screen")
  }
}

// ======================================================
// ================= AppearanceScreen ===================
// ======================================================

sealed class AppearanceScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = AppearanceScreen.screenIdentifier()
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
      override fun screenIdentifier(): ScreenIdentifier = AppearanceScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  sealed class LayoutGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = LayoutGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object BottomNavigationMode : LayoutGroup("bottom_navigation_mode")
    object LayoutMode : LayoutGroup("layout_mode")
    object CatalogPostAlignmentMode : LayoutGroup("catalog_post_alignment_mode")
    object ThreadPostAlignmentMode : LayoutGroup("thread_post_alignment_mode")
    object CatalogColumnsCount : LayoutGroup("catalog_columns_count")
    object AlbumColumnsCount : LayoutGroup("album_columns_count")
    object NeverHideToolbar : LayoutGroup("never_hide_toolbar")
    object EnableReplyFAB : LayoutGroup("enable_reply_fab")
    object BottomJsCaptcha : LayoutGroup("bottom_js_captcha")
    object NeverShowPages : LayoutGroup("never_show_pages")
    object EnableDraggableScrollbars : LayoutGroup("enable_draggable_scrollbars")
    object ReorderableBottomNavViewButtonsSetting : LayoutGroup("reorderable_bottom_nav_view_buttons")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = AppearanceScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("layout_group")
    }
  }

  sealed class PostGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = PostGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object FontSize : PostGroup("font_size")
    object PostCellThumbnailSizePercent : PostGroup("post_cell_thumbnail_size_percent")
    object PostThumbnailScaling : PostGroup("post_thumbnail_scaling")
    object PostFullDate : PostGroup("post_full_date")
    object PostFullDateUseLocalLocale : PostGroup("post_full_date_use_local_locale")
    object DrawPostThumbnailBackground : PostGroup("draw_post_thumbnail_background")
    object PostFileInfo : PostGroup("post_file_info")
    object ShiftPostComment : PostGroup("shift_post_comment")
    object ForceShiftPostComment : PostGroup("force_shift_post_comment")
    object PostMultipleImagesCompactMode : PostGroup("post_multiple_images_compact_mode")
    object TextOnly : PostGroup("text_only")
    object RevealTextSpoilers : PostGroup("reveal_text_spoilers")
    object Anonymize : PostGroup("anonymize")
    object ShowAnonymousName : PostGroup("show_anonymous_name")
    object AnonymizeIds : PostGroup("anonymize_ids")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = AppearanceScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("post_group")
    }
  }

  sealed class PostLinksGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = PostLinksGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ParseYoutubeTitlesAndDuration : PostLinksGroup("parse_youtube_titles_and_duration")
    object ParseSoundCloudTitlesAndDuration : PostLinksGroup("parse_soundcloud_titles_and_duration")
    object ParseStreamableTitlesAndDuration : PostLinksGroup("parse_streamable_titles_and_duration")
    object ShowLinkAlongWithTitleAndDuration : PostLinksGroup("show_link_along_with_title_and_duration")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = AppearanceScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("post_links_group")
    }
  }

  sealed class ImagesGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = ImagesGroup.getGroupIdentifier()
  ) : IGroup,
    AppearanceScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object HideImages : ImagesGroup("hide_images")
    object RemoveImageSpoilers : ImagesGroup("remove_image_spoilers")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = AppearanceScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("images_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("appearance_screen")
  }
}

// ====================================================
// ================= BehaviorScreen ===================
// ====================================================

sealed class BehaviorScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = BehaviorScreen.screenIdentifier()
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
    object ViewThreadControllerSwipeable : GeneralGroup("view_thread_controller_swipeable")
    object ReplyLayoutOpenCloseGestures : GeneralGroup("reply_layout_open_close_gestures")
    object OpenLinkConfirmation : GeneralGroup("open_link_confirmation")
    object CaptchaSetup : GeneralGroup("catpcha_setup")
    object JsCaptchaCookiesEditor : GeneralGroup("js_captcha_cookies_editor")
    object ClearPostHides : GeneralGroup("clear_post_hides")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = BehaviorScreen.screenIdentifier()
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
      override fun screenIdentifier(): ScreenIdentifier = BehaviorScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("replies_group")
    }
  }

  sealed class PostGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object VolumeKeysScrolling : PostGroup("volume_keys_scrolling")
    object TapNoReply : PostGroup("tap_no_reply")
    object PostLinksTakeWholeHorizSpace : PostGroup("post_links_take_whole_horiz_space")
    object MarkUnseenPosts : PostGroup("mark_unseen_posts")
    object MarkSeenThreads : PostGroup("mark_seen_threads")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = BehaviorScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("post_group")
    }
  }

  sealed class Android10GestureSettings(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object GesturesExclusionZonesEditor : Android10GestureSettings("gestures_exclusion_zones_editor")
    object ResetExclusionZones : Android10GestureSettings("reset_exclusion_zones")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = BehaviorScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("android_10_gesture_settings")
    }
  }


  sealed class OtherSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = GeneralGroup.getGroupIdentifier()
  ) : IGroup,
    BehaviorScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object FullUserRotationEnable : OtherSettingsGroup("full_user_rotation")
    object ShowCopyApkUpdateDialog : OtherSettingsGroup("show_copy_apk_update_dialog")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = BehaviorScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("other_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("behavior_screen")
  }
}

// =================================================
// ================= MediaScreen ===================
// =================================================

sealed class MediaScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = MediaScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MediaSavingGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = getGroupIdentifier()
  ) : IGroup, MediaScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object SaveLocation : MediaSavingGroup("save_location")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = MediaScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("media_group")
    }

  }

  sealed class LoadingGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = getGroupIdentifier()
  ) : IGroup,
    MediaScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ImageAutoLoadNetwork : LoadingGroup("image_auto_load_network")
    object VideoAutoLoadNetwork : LoadingGroup("video_auto_load_network")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = MediaScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("loading_group")
    }
  }

  sealed class MiscGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = getGroupIdentifier()
  ) : IGroup,
    MediaScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object AlwaysRandomizeFileNameWhenPickingFiles : MiscGroup("always_randomize_file_name_when_picking_files")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = MediaScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("misc_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("media_screen")
  }
}

// ========================================================
// ================= ImportExportScreen ===================
// ========================================================

sealed class ImportExportScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = ImportExportScreen.screenIdentifier()
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
      override fun screenIdentifier(): ScreenIdentifier = ImportExportScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_settings_group")
    }
  }

  sealed class ImportFromKurobaSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainSettingsGroup.getGroupIdentifier()
  ) : IGroup,
    ImportExportScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ImportSettingsFromKuroba : ImportFromKurobaSettingsGroup("import_settings_from_kuroba")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = ImportExportScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("import_from_kurpba_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("import_export_screen")
  }
}

// ================================================================
// ================= SecuritySettingsScreen =======================
// ================================================================

sealed class SecurityScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = SecurityScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainSettingsGroup.getGroupIdentifier()
  ) : IGroup,
    SecurityScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object Proxy : MainSettingsGroup("proxy")
    object ForceHttpsScheme : MainSettingsGroup("force_https_scheme")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = SecurityScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("security_screen")
  }
}

// ================================================================
// ================= CachingSettingsScreen =======================
// ================================================================

sealed class CachingScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = SecurityScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MediaCacheSizeGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MediaCacheSizeGroup.getGroupIdentifier()
  ) : IGroup,
    CachingScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object NormalCacheSize : MediaCacheSizeGroup("normal_cache_size")
    object PrefetchCacheSize : MediaCacheSizeGroup("prefetch_cache_size")
    object MediaCacheCleanupRemoveFilesPercent : MediaCacheSizeGroup("media_cache_cleanup_remove_files_percent")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = CachingScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("media_cache_size_group")
    }
  }

  sealed class CacheGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = CacheGroup.getGroupIdentifier()
  ) : IGroup,
    CachingScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    data class ClearFileCache(val cacheFileTypeName: String) : CacheGroup("clear_file_cache_${cacheFileTypeName}")
    object ClearExoPlayerCache : CacheGroup("clear_exo_player_cache")
    object ThreadDownloadCacheSize : CacheGroup("thread_download_cache_size")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = CachingScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("cache_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("caching_screen")
  }
}

// ================================================================
// ================= PluginsSettingsScreen =======================
// ================================================================

sealed class PluginsScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = SecurityScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MpvPluginGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MpvPluginGroup.getGroupIdentifier()
  ) : IGroup,
    PluginsScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object UseMpv : MpvPluginGroup("use_mpv")
    object UseConfigFile : MpvPluginGroup("use_config_file")
    object EditConfigFile : MpvPluginGroup("edit_config_file")
    object CheckMpvLibsState : MpvPluginGroup("check_mpv_libs_state")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = MediaScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("mpv_plugin_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("plugins_screen")
  }
}

// ================================================================
// ================= CaptchaSolversSettingsScreen ===================
// ================================================================

sealed class CaptchaSolversScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = CaptchaSolversScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class TwoCaptchaSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = TwoCaptchaSettingsGroup.getGroupIdentifier()
  ) : IGroup,
    CaptchaSolversScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object TwoCaptchaSolverEnabled : TwoCaptchaSettingsGroup("two_captcha_solver_enabled")
    object TwoCaptchaSolverUrl : TwoCaptchaSettingsGroup("two_captcha_solver_url")
    object TwoCaptchaSolverApiKey : TwoCaptchaSettingsGroup("two_captcha_solver_api_key")
    object TwoCaptchaSolverValidate : TwoCaptchaSettingsGroup("two_captcha_solver_validate")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = CaptchaSolversScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("two_captcha_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("captcha_solvers_screen")
  }
}


// ================================================================
// ================= ExperimentalSettingsScreen ===================
// ================================================================

sealed class ExperimentalScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  screenIdentifier: ScreenIdentifier = ExperimentalScreen.screenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainSettingsGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainSettingsGroup.getGroupIdentifier()
  ) : IGroup,
    ExperimentalScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object OkHttpAllowHttp2 : MainSettingsGroup("ok_http_allow_http_2")
    object OkHttpAllowIpv6 : MainSettingsGroup("ok_http_allow_ipv6")
    object OkHttpUseDnsOverHttps : MainSettingsGroup("ok_http_use_dns_over_https")
    object CloudflareForcePreload : MainSettingsGroup("cloudflare_force_preload")
    object AutoLoadThreadImages : MainSettingsGroup("auto_load_thread_images")
    object ShowPrefetchLoadingIndicator : MainSettingsGroup("show_prefetch_loading_indicator")
    object HighResCells : MainSettingsGroup("high_res_cells")
    object ColorizeTextSelectionCursors : MainSettingsGroup("colorize_text_selection_cursors")
    object DonateCaptchaForGreaterGood : MainSettingsGroup("donate_captcha_for_greater_good")
    object CustomUserAgent : MainSettingsGroup("override_user_agent")

    companion object : IGroupIdentifier() {
      override fun screenIdentifier(): ScreenIdentifier = ExperimentalScreen.screenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_settings_group")
    }
  }

  companion object : IScreenIdentifier() {
    override fun screenIdentifier(): ScreenIdentifier = ScreenIdentifier("experimental_screen")
  }
}