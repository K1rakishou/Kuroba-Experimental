package com.github.adamantcheese.chan.features.settings.screens.delegate

import android.Manifest
import android.content.Context
import android.widget.Toast
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager
import com.github.adamantcheese.chan.features.settings.MediaScreen
import com.github.adamantcheese.chan.features.settings.SettingsCoordinatorCallbacks
import com.github.adamantcheese.chan.features.settings.screens.delegate.base_directory.MediaSettingsControllerPresenter
import com.github.adamantcheese.chan.features.settings.screens.delegate.base_directory.SaveLocationSetupDelegate
import com.github.adamantcheese.chan.features.settings.screens.delegate.base_directory.SharedLocationSetupDelegate
import com.github.adamantcheese.chan.features.settings.screens.delegate.base_directory.ThreadsLocationSetupDelegate
import com.github.adamantcheese.chan.ui.controller.LoadingViewController
import com.github.adamantcheese.chan.ui.controller.SaveLocationController
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager

class MediaSettingsDelegate(
  private val context: Context,
  private val callback: SettingsCoordinatorCallbacks,
  private val navigationController: NavigationController,
  private val fileManager: FileManager,
  private val fileChooser: FileChooser,
  private val databaseManager: DatabaseManager,
  private val threadSaveManager: ThreadSaveManager,
  private val runtimePermissionsHelper: RuntimePermissionsHelper
) : SaveLocationSetupDelegate.MediaControllerCallbacks,
  ThreadsLocationSetupDelegate.MediaControllerCallbacks {
  private var initialized = false

  private val presenter by lazy {
    MediaSettingsControllerPresenter(
      fileManager,
      fileChooser,
      context
    )
  }

  private val sharedLocationSetupDelegate by lazy {
    SharedLocationSetupDelegate(
      context,
      this,
      presenter,
      fileManager
    )
  }

  private val saveLocationSetupDelegate by lazy {
    SaveLocationSetupDelegate(
      context,
      this,
      presenter
    )
  }

  private val threadsLocationSetupDelegate by lazy {
    ThreadsLocationSetupDelegate(
      context,
      this,
      presenter,
      databaseManager,
      threadSaveManager
    )
  }

  fun onCreate() {
    check(!initialized) { "Already initialized!" }
    presenter.onCreate(sharedLocationSetupDelegate)

    initialized = true
  }

  fun onDestroy() {
    check(initialized) { "Already destroyed!" }
    presenter.onDestroy()

    initialized = false
  }

  override fun runWithWritePermissionsOrShowErrorToast(func: Runnable) {
    BackgroundUtils.ensureMainThread()

    if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      func.run()
      return
    }

    runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
      if (!granted) {
        showToast(
          context,
          R.string.media_settings_cannot_continue_write_permission,
          Toast.LENGTH_LONG
        )
      } else {
        func.run()
      }
    }
  }

  override fun pushController(saveLocationController: SaveLocationController) {
    BackgroundUtils.ensureMainThread()
    navigationController.pushController(saveLocationController)
  }

  override fun updateThreadsLocationViewText(newLocation: String) {
    callback.rebuildSetting(
      MediaScreen,
      MediaScreen.MediaSavingGroup,
      MediaScreen.MediaSavingGroup.ThreadSaveLocation
    )
  }

  override fun updateSaveLocationViewText(newLocation: String) {
    callback.rebuildSetting(
      MediaScreen,
      MediaScreen.MediaSavingGroup,
      MediaScreen.MediaSavingGroup.MediaSaveLocation
    )
  }

  override fun presentController(loadingViewController: LoadingViewController) {
    BackgroundUtils.ensureMainThread()
    navigationController.presentController(loadingViewController)
  }

  override fun onFilesBaseDirectoryReset() {
    BackgroundUtils.ensureMainThread()
    showToast(context, R.string.media_settings_base_dir_reset_message)
  }

  override fun onLocalThreadsBaseDirectoryReset() {
    BackgroundUtils.ensureMainThread()
    showToast(context, R.string.media_settings_base_dir_reset_message)
  }

  override fun onCouldNotCreateDefaultBaseDir(path: String) {
    BackgroundUtils.ensureMainThread()
    showToast(context, context.getString(R.string.media_settings_could_not_create_default_baseDir, path))
  }

  fun getSaveLocation(): String {
    return saveLocationSetupDelegate.getSaveLocation()
  }

  fun showUseSAFOrOldAPIForSaveLocationDialog() {
    saveLocationSetupDelegate.showUseSAFOrOldAPIForSaveLocationDialog()
  }

  fun getLocalThreadsLocation(): String {
    return threadsLocationSetupDelegate.getLocalThreadsLocation()
  }

  fun showUseSAFOrOldAPIForLocalThreadsLocationDialog() {
    threadsLocationSetupDelegate.showUseSAFOrOldAPIForLocalThreadsLocationDialog()
  }

}