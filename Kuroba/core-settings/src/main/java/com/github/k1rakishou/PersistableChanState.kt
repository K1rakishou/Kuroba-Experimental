package com.github.k1rakishou

import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.IntegerSetting
import com.github.k1rakishou.prefs.LongSetting
import com.github.k1rakishou.prefs.StringSetting
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

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
  lateinit var safExplanationMessageShown: BooleanSetting
  @JvmStatic
  lateinit var themesIgnoreSystemDayNightModeMessageShown: BooleanSetting
  @JvmStatic
  lateinit var bookmarksLastOpenedTabPageIndex: IntegerSetting
  @JvmStatic
  lateinit var imageViewerImmersiveModeEnabled: BooleanSetting

  fun init(persistableChanStateInfo: PersistableChanStateInfo) {
    this.persistableChanStateInfo = persistableChanStateInfo

    initInternal()
  }

  private fun initInternal() {
    try {
      val provider = SharedPreferencesSettingProvider(AndroidUtils.getAppState())
      watchLastCount = IntegerSetting(provider, "watch_last_count", 0)
      hasNewApkUpdate =
        BooleanSetting(
          provider,
          "has_new_apk_update",
          false
        )
      previousVersion = IntegerSetting(provider, "previous_version", persistableChanStateInfo.versionCode)
      updateCheckTime = LongSetting(provider, "update_check_time", 0L)
      previousDevHash = StringSetting(provider, "previous_dev_hash", persistableChanStateInfo.commitHash)
      viewThreadBookmarksGridMode = BooleanSetting(
        provider,
        "view_thread_bookmarks_grid_mode",
        true
      )

      shittyPhonesBackgroundLimitationsExplanationDialogShown =
        BooleanSetting(
          provider,
          "shitty_phones_background_limitations_explanation_dialog_shown",
          false
        )

      bookmarksRecyclerIndexAndTop = StringSetting(
        provider,
        "bookmarks_recycler_index_and_top",
        RecyclerIndexAndTopInfo.bookmarksControllerDefaultJson(viewThreadBookmarksGridMode)
      )

      filterWatchesRecyclerIndexAndTop = StringSetting(
        provider,
        "filter_watches_recycler_index_and_top",
        RecyclerIndexAndTopInfo.filterWatchesControllerDefaultJson()
      )

      proxyEditingNotificationShown = BooleanSetting(
        provider,
        "proxy_editing_notification_shown",
        false
      )

      lastRememberedFilePicker = StringSetting(
        provider,
        "last_remembered_file_picker",
        ""
      )

      safExplanationMessageShown = BooleanSetting(
        provider,
        "saf_explanation_message_shown",
        false
      )

      themesIgnoreSystemDayNightModeMessageShown = BooleanSetting(
        provider,
        "themes_ignore_system_day_night_mode_message_shown",
        false
      )
      bookmarksLastOpenedTabPageIndex = IntegerSetting(
        provider,
        "bookmarks_last_opened_tab_page_index",
        -1
      )

      imageViewerImmersiveModeEnabled = BooleanSetting(
        provider,
        "image_viewer_immersive_mode_enabled",
        true
      )
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

  data class RecyclerIndexAndTopInfo(
    @SerializedName("is_for_grid_layout_manager")
    val isForGridLayoutManager: Boolean,
    @SerializedName("index_and_top")
    val indexAndTop: IndexAndTop = IndexAndTop()
  ) {
    companion object {
      fun bookmarksControllerDefaultJson(viewThreadBookmarksGridMode: BooleanSetting): String {
        return gson.toJson(
          RecyclerIndexAndTopInfo(isForGridLayoutManager = viewThreadBookmarksGridMode.default)
        )
      }

      fun filterWatchesControllerDefaultJson(): String {
        val recyclerIndexAndTopInfo = RecyclerIndexAndTopInfo(
          isForGridLayoutManager = true,
          indexAndTop = IndexAndTop()
        )

        return gson.toJson(recyclerIndexAndTopInfo)
      }
    }
  }

  data class IndexAndTop(
    @SerializedName("index")
    var index: Int = 0,
    @SerializedName("top")
    var top: Int = 0
  )
}