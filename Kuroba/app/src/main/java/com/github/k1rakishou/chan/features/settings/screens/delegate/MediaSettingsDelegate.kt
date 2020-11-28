package com.github.k1rakishou.chan.features.settings.screens.delegate

import android.Manifest
import android.content.Context
import android.widget.Toast
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.MediaScreen
import com.github.k1rakishou.chan.features.settings.SettingsCoordinatorCallbacks
import com.github.k1rakishou.chan.features.settings.screens.delegate.base_directory.MediaSettingsControllerPresenter
import com.github.k1rakishou.chan.features.settings.screens.delegate.base_directory.SaveLocationSetupDelegate
import com.github.k1rakishou.chan.features.settings.screens.delegate.base_directory.SharedLocationSetupDelegate
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.SaveLocationController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager

class MediaSettingsDelegate(
  private val context: Context,
  private val callback: SettingsCoordinatorCallbacks,
  private val navigationController: NavigationController,
  private val fileManager: FileManager,
  private val fileChooser: FileChooser,
  private val runtimePermissionsHelper: RuntimePermissionsHelper,
  private val dialogFactory: DialogFactory
) : SaveLocationSetupDelegate.MediaControllerCallbacks {
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
      fileManager,
      dialogFactory
    )
  }

  private val saveLocationSetupDelegate by lazy {
    SaveLocationSetupDelegate(
      context,
      this,
      presenter,
      dialogFactory
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

  override fun onCouldNotCreateDefaultBaseDir(path: String) {
    BackgroundUtils.ensureMainThread()
    showToast(context, context.getString(R.string.media_settings_could_not_create_default_baseDir, path))
  }

  fun getSaveLocation(): String {
    return saveLocationSetupDelegate.getSaveLocation()
  }

  fun showUseSAFOrOldAPIForSaveLocationDialog() {
    if (AndroidUtils.isAndroid11()) {
      saveLocationSetupDelegate.useSAFLocationPicker()
    } else {
      saveLocationSetupDelegate.showUseSAFOrOldAPIForSaveLocationDialog()
    }
  }

}