package com.github.k1rakishou.v2

import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import com.github.k1rakishou.v2.parameters.ApkUpdateInfoJson
import com.github.k1rakishou.v2.parameters.ImageSaverV2Options
import com.github.k1rakishou.v2.parameters.RecyclerIndexAndTopInfo
import com.github.k1rakishou.v2.parameters.RecyclerIndexAndTopInfo.IndexAndTop
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings
import com.github.k1rakishou.v2.parameters.ReorderableBottomNavViewButtons
import com.github.k1rakishou.v2.parameters.ReorderableMediaViewerActions
import com.github.k1rakishou.v2.parameters.ThreadDownloaderOptions
import com.github.k1rakishou.v2.settings.KurobaMoshiSetting

class InternalSettings(
  database: KurobaSettingsDatabase,
  override val initialSettingsState: KurobaInitialSettingsState
) : BaseSettings(database) {
  override val backupable: Boolean = true

  val hasNewApkUpdate by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.HasNewApkUpdate, false)
  }
  val updateCheckTime by lazy {
    createLongSetting(KurobaSettingKey.Internal.UpdateCheckTime, 0L)
  }
  val previousBuildNumber by lazy {
    createLongSetting(KurobaSettingKey.Internal.PreviousBuildNumber, -1L)
  }
  val apkUpdateInfoJson by lazy {
    createMoshiSetting<ApkUpdateInfoJson>(
      clazz = ApkUpdateInfoJson::class.java,
      key = KurobaSettingKey.Internal.ApkUpdateInfoJson,
      default = ApkUpdateInfoJson()
    )
  }
  val viewThreadBookmarksGridMode by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.ViewThreadBookmarksGridMode, true)
  }
  val albumLayoutGridMode by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.AlbumLayoutGridMode, false)
  }
  val shittyPhonesBackgroundLimitationsExplanationDialogShown by lazy {
    createBooleanSetting(
      key = KurobaSettingKey.Internal.ShittyPhonesBackgroundLimitationsExplanationDialogShown,
      default = false
    )
  }
  val bookmarksRecyclerIndexAndTop by lazy {
    createMoshiSetting(
      clazz = RecyclerIndexAndTopInfo::class.java,
      key = KurobaSettingKey.Internal.BookmarksRecyclerIndexAndTop,
      default = RecyclerIndexAndTopInfo(isForGridLayoutManager = viewThreadBookmarksGridMode.default)
    )
  }
  val proxyEditingNotificationShown by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.ProxyEditingNotificationShown, false)
  }
  val lastRememberedFilePicker by lazy {
    createStringSetting(KurobaSettingKey.Internal.LastRememberedFilePicker, "")
  }
  val themesIgnoreSystemDayNightModeMessageShown by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.ThemesIgnoreSystemDayNightModeMessageShown, false)
  }
  val imageViewerImmersiveModeEnabled by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.ImageViewerImmersiveModeEnabled, true)
  }
  val imageSaverV2PersistedOptions by lazy {
    createMoshiSetting<ImageSaverV2Options>(
      clazz = ImageSaverV2Options::class.java,
      key = KurobaSettingKey.Internal.ImageSaverV2PersistedOptions,
      default = ImageSaverV2Options()
    )
  }
  val reorderableBottomNavViewButtons by lazy {
    createMoshiSetting<ReorderableBottomNavViewButtons>(
      clazz = ReorderableBottomNavViewButtons::class.java,
      key = KurobaSettingKey.Internal.ReorderableBottomNavViewButtons,
      default = ReorderableBottomNavViewButtons()
    )
  }
  val reorderableMediaViewerActions by lazy {
    createMoshiSetting<ReorderableMediaViewerActions>(
      clazz = ReorderableMediaViewerActions::class.java,
      key = KurobaSettingKey.Internal.ReorderableMediaViewerActions,
      default = ReorderableMediaViewerActions()
    )
  }
  val showAlbumViewsImageDetails by lazy {
    createBooleanSetting(
      key = KurobaSettingKey.Internal.ShowAlbumViewsImageDetails,
      default = true
    )
  }
  val threadDownloaderOptions by lazy {
    createMoshiSetting<ThreadDownloaderOptions>(
      clazz = ThreadDownloaderOptions::class.java,
      key = KurobaSettingKey.Internal.ThreadDownloaderOptions,
      default = ThreadDownloaderOptions()
    )
  }
  val threadDownloaderArchiveWarningShown by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.ThreadDownloaderArchiveWarningShown, false)
  }
  val dontKeepActivitiesWarningShown by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.DontKeepActivitiesWarningShown, false)
  }
  val remoteImageSearchSettings by lazy {
    createMoshiSetting<RemoteImageSearchSettings>(
      clazz = RemoteImageSearchSettings::class.java,
      key = KurobaSettingKey.Internal.RemoteImageSearchSettings,
      default = RemoteImageSearchSettings.defaults()
    )
  }
  val newReplyLayoutTutorialFinished by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.NewReplyLayoutTutorialFinished, false)
  }
  val alwaysRandomizePickedFilesNames by lazy {
    createBooleanSetting(KurobaSettingKey.Internal.AlwaysRandomizePickedFilesNames, false)
  }

  fun storeRecyclerIndexAndTopInfo(
    setting: KurobaMoshiSetting<RecyclerIndexAndTopInfo>,
    isForGridLayoutManager: Boolean,
    indexAndTop: IndexAndTop
  ) {
    setting.writeAsync(RecyclerIndexAndTopInfo(isForGridLayoutManager, indexAndTop))
  }

  fun getRecyclerIndexAndTopInfo(
    setting: KurobaMoshiSetting<RecyclerIndexAndTopInfo>,
    isForGridLayoutManager: Boolean
  ): RecyclerIndexAndTopInfo.IndexAndTop {
    val info = setting.readBlocking()

    if (info.isForGridLayoutManager == isForGridLayoutManager) {
      // If we are trying to restore index and top for RecyclerView with the same layout manager
      // then we can use the "top" parameter, otherwise we can't so we need to make it 0
      return info.indexAndTop
    }

    return RecyclerIndexAndTopInfo.IndexAndTop(info.indexAndTop.index, 0)
  }
}