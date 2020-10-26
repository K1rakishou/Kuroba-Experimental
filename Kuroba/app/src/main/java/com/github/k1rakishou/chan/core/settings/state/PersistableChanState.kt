package com.github.k1rakishou.chan.core.settings.state

import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.core.settings.*
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * This state class acts in a similar manner to [ChanSettings], but everything here is not exported; this data is
 * strictly for use internally to the application and acts as a helper to ensure that data is not lost.
 */
object PersistableChanState {
  private const val TAG = "ChanState"

  private val gson = Gson().newBuilder().create()

  @JvmField
  val watchLastCount: IntegerSetting
  @JvmField
  val hasNewApkUpdate: BooleanSetting
  @JvmField
  val previousVersion: IntegerSetting
  @JvmField
  val updateCheckTime: LongSetting
  @JvmField
  val previousDevHash: StringSetting
  @JvmField
  val viewThreadBookmarksGridMode: BooleanSetting
  @JvmField
  val shittyPhonesBackgroundLimitationsExplanationDialogShown: BooleanSetting
  @JvmField
  val bookmarksRecyclerIndexAndTop: StringSetting
  @JvmField
  val proxyEditingNotificationShown: BooleanSetting

  init {
    try {
      val p = SharedPreferencesSettingProvider(AndroidUtils.getAppState())
      watchLastCount = IntegerSetting(p, "watch_last_count", 0)
      hasNewApkUpdate = BooleanSetting(p, "has_new_apk_update", false)
      previousVersion = IntegerSetting(p, "previous_version", BuildConfig.VERSION_CODE)
      updateCheckTime = LongSetting(p, "update_check_time", 0L)
      previousDevHash = StringSetting(p, "previous_dev_hash", BuildConfig.COMMIT_HASH)
      viewThreadBookmarksGridMode = BooleanSetting(p, "view_thread_bookmarks_grid_mode", true)

      shittyPhonesBackgroundLimitationsExplanationDialogShown = BooleanSetting(
        p,
        "shitty_phones_background_limitations_explanation_dialog_shown",
        false
      )

      bookmarksRecyclerIndexAndTop = StringSetting(
        p,
        "bookmarks_recycler_index_and_top",
        BookmarksRecyclerIndexAndTopInfo.defaultJson(viewThreadBookmarksGridMode)
      )

      proxyEditingNotificationShown = BooleanSetting(
        p,
        "proxy_editing_notification_shown",
        false
      )
    } catch (e: Exception) {
      Logger.e(TAG, "Error while initializing the state", e)
      throw e
    }
  }

  fun storeBookmarksRecyclerIndexAndTopInfo(
    isForGridLayoutManager: Boolean,
    indexAndTop: RecyclerUtils.IndexAndTop
  ) {
    val setting = requireNotNull(bookmarksRecyclerIndexAndTop) { "bookmarksRecyclerIndexAndTop is null" }

    val json = gson.toJson(BookmarksRecyclerIndexAndTopInfo(isForGridLayoutManager, indexAndTop))
    setting.set(json)
  }

  fun getBookmarksRecyclerIndexAndTopInfo(isForGridLayoutManager: Boolean): RecyclerUtils.IndexAndTop {
    val setting = requireNotNull(bookmarksRecyclerIndexAndTop) { "bookmarksRecyclerIndexAndTop is null" }
    val info = gson.fromJson(setting.get(), BookmarksRecyclerIndexAndTopInfo::class.java)

    if (info.isForGridLayoutManager == isForGridLayoutManager) {
      // If we are trying to restore index and top for RecyclerView with the same layout manager
      // then we can use the "top" parameter, otherwise we can't so we need to make it 0
      return info.indexAndTop
    }

    return RecyclerUtils.IndexAndTop(info.indexAndTop.index, 0)
  }

  data class BookmarksRecyclerIndexAndTopInfo(
    @SerializedName("is_for_grid_layout_manager")
    val isForGridLayoutManager: Boolean,
    @SerializedName("index_and_top")
    val indexAndTop: RecyclerUtils.IndexAndTop = RecyclerUtils.IndexAndTop()
  ) {
    companion object {
      fun defaultJson(viewThreadBookmarksGridMode: BooleanSetting): String {
        return gson.toJson(
          BookmarksRecyclerIndexAndTopInfo(isForGridLayoutManager = viewThreadBookmarksGridMode.default)
        )
      }
    }
  }
}