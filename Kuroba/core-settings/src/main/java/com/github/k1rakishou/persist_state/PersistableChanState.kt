package com.github.k1rakishou.persist_state

import com.github.k1rakishou.PersistableChanStateInfo
import com.github.k1rakishou.ReorderableBottomNavViewButtons
import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.IntegerSetting
import com.github.k1rakishou.prefs.JsonSetting
import com.github.k1rakishou.prefs.LongSetting
import com.github.k1rakishou.prefs.StringSetting
import com.google.gson.Gson

/**
 * This state class acts in a similar manner to [ChanSettings], but everything here is not exported; this data is
 * strictly for use internally to the application and acts as a helper to ensure that data is not lost.
 */
object PersistableChanState {
  private const val TAG = "ChanState"
  private lateinit var persistableChanStateInfo: PersistableChanStateInfo

  private val gson = Gson()
    .newBuilder()
    .create()

  @JvmStatic
  lateinit var watchLastCount: IntegerSetting
  @JvmStatic
  lateinit var hasNewApkUpdate: BooleanSetting
  @JvmStatic
  lateinit var previousVersion: IntegerSetting
  @JvmStatic
  lateinit var updateCheckTime: LongSetting
  @JvmStatic
  lateinit var previousDevHash: StringSetting
  @JvmStatic
  lateinit var viewThreadBookmarksGridMode: BooleanSetting
  @JvmStatic
  lateinit var albumLayoutGridMode: BooleanSetting
  @JvmStatic
  lateinit var shittyPhonesBackgroundLimitationsExplanationDialogShown: BooleanSetting
  @JvmStatic
  lateinit var bookmarksRecyclerIndexAndTop: StringSetting
  @JvmStatic
  lateinit var filterWatchesRecyclerIndexAndTop: StringSetting
  @JvmStatic
  lateinit var proxyEditingNotificationShown: BooleanSetting
  @JvmStatic
  lateinit var lastRememberedFilePicker: StringSetting
  @JvmStatic
  lateinit var themesIgnoreSystemDayNightModeMessageShown: BooleanSetting
  @JvmStatic
  lateinit var bookmarksLastOpenedTabPageIndex: IntegerSetting
  @JvmStatic
  lateinit var imageViewerImmersiveModeEnabled: BooleanSetting
  @JvmStatic
  lateinit var imageSaverV2PersistedOptions: JsonSetting<ImageSaverV2Options>
  @JvmStatic
  lateinit var reorderableBottomNavViewButtons: JsonSetting<ReorderableBottomNavViewButtons>
  @JvmStatic
  lateinit var showAlbumViewsImageDetails: BooleanSetting
  @JvmStatic
  lateinit var drawerNavHistoryGridMode: BooleanSetting
  @JvmStatic
  lateinit var boardSelectionGridMode: BooleanSetting
  @JvmStatic
  lateinit var threadDownloaderOptions: JsonSetting<ThreadDownloaderOptions>
  @JvmStatic
  lateinit var threadDownloaderArchiveWarningShown: BooleanSetting

  // TODO(KurobaEx): remove in v0.11.x
  @JvmStatic
  @Deprecated("remove me once v0.11.x is released")
  lateinit var appHack_V08X_deleteAllBlockedBookmarkWatcherWorkDone: BooleanSetting
  // TODO(KurobaEx): remove in v0.11.x
  @JvmStatic
  @Deprecated("remove me once v0.11.x is released")
  lateinit var appHack_V08X_deleteAllBlockedFilterWatcherWorkDone: BooleanSetting

  fun init(persistableChanStateInfo: PersistableChanStateInfo) {
    PersistableChanState.persistableChanStateInfo = persistableChanStateInfo

    initInternal()
  }

  private fun initInternal() {
    try {
      val provider = SharedPreferencesSettingProvider(AndroidUtils.getAppState())
      watchLastCount = IntegerSetting(provider, "watch_last_count", 0)
      hasNewApkUpdate = BooleanSetting(provider, "has_new_apk_update", false
      )
      previousVersion = IntegerSetting(provider, "previous_version", persistableChanStateInfo.versionCode)
      updateCheckTime = LongSetting(provider, "update_check_time", 0L)
      previousDevHash = StringSetting(provider, "previous_dev_hash", persistableChanStateInfo.commitHash)
      viewThreadBookmarksGridMode = BooleanSetting(provider, "view_thread_bookmarks_grid_mode", true)
      albumLayoutGridMode = BooleanSetting(provider, "album_layout_grid_mode", false)

      shittyPhonesBackgroundLimitationsExplanationDialogShown = BooleanSetting(
        provider,
        "shitty_phones_background_limitations_explanation_dialog_shown",
        false
      )

      bookmarksRecyclerIndexAndTop = StringSetting(
        provider,
        "bookmarks_recycler_index_and_top",
        RecyclerIndexAndTopInfo.bookmarksControllerDefaultJson(gson, viewThreadBookmarksGridMode)
      )

      filterWatchesRecyclerIndexAndTop = StringSetting(
        provider,
        "filter_watches_recycler_index_and_top",
        RecyclerIndexAndTopInfo.filterWatchesControllerDefaultJson(gson)
      )

      proxyEditingNotificationShown = BooleanSetting(provider, "proxy_editing_notification_shown", false)
      lastRememberedFilePicker = StringSetting(provider, "last_remembered_file_picker", "")
      themesIgnoreSystemDayNightModeMessageShown = BooleanSetting(provider, "themes_ignore_system_day_night_mode_message_shown", false)
      bookmarksLastOpenedTabPageIndex = IntegerSetting(provider, "bookmarks_last_opened_tab_page_index", -1)
      imageViewerImmersiveModeEnabled = BooleanSetting(provider, "image_viewer_immersive_mode_enabled", true)

      imageSaverV2PersistedOptions = JsonSetting(
        gson,
        ImageSaverV2Options::class.java,
        provider,
        "image_saver_options",
        ImageSaverV2Options()
      )

      reorderableBottomNavViewButtons = JsonSetting(
        gson,
        ReorderableBottomNavViewButtons::class.java,
        provider,
        "bottom_nav_view_buttons_ordered",
        ReorderableBottomNavViewButtons()
      )

      showAlbumViewsImageDetails = BooleanSetting(provider, "show_album_views_image_details", true)
      drawerNavHistoryGridMode = BooleanSetting(provider, "drawer_nav_history_grid_mode", false)
      boardSelectionGridMode = BooleanSetting(provider, "board_selection_grid_mode", false)

      threadDownloaderOptions = JsonSetting(
        gson,
        ThreadDownloaderOptions::class.java,
        provider,
        "thread_downloader_options",
        ThreadDownloaderOptions()
      )

      threadDownloaderArchiveWarningShown = BooleanSetting(provider, "thread_downloader_archive_warning_shown", false)

      appHack_V08X_deleteAllBlockedBookmarkWatcherWorkDone = BooleanSetting(provider, "app_hack_v08x_delete_all_blocked_bookmark_watcher_work_done", false)
      appHack_V08X_deleteAllBlockedFilterWatcherWorkDone = BooleanSetting(provider, "app_hack_v08x_delete_all_blocked_filter_watcher_work_done", false)
    } catch (e: Exception) {
      Logger.e(TAG, "Error while initializing the state", e)
      throw e
    }
  }

  fun storeRecyclerIndexAndTopInfo(
    setting: StringSetting,
    isForGridLayoutManager: Boolean,
    indexAndTop: IndexAndTop
  ) {
    val json = gson.toJson(RecyclerIndexAndTopInfo(isForGridLayoutManager, indexAndTop))
    setting.set(json)
  }

  fun getRecyclerIndexAndTopInfo(
    setting: StringSetting,
    isForGridLayoutManager: Boolean
  ): IndexAndTop {
    val info = gson.fromJson(setting.get(), RecyclerIndexAndTopInfo::class.java)

    if (info.isForGridLayoutManager == isForGridLayoutManager) {
      // If we are trying to restore index and top for RecyclerView with the same layout manager
      // then we can use the "top" parameter, otherwise we can't so we need to make it 0
      return info.indexAndTop
    }

    return IndexAndTop(info.indexAndTop.index, 0)
  }

}