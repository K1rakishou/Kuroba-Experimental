package com.github.k1rakishou.chan.features.settings.screens.delegate.base_directory

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.PersistableChanState
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.SaveLocationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import java.io.File

class SaveLocationSetupDelegate(
  private val context: Context,
  private val callbacks: MediaControllerCallbacks,
  private val presenter: MediaSettingsControllerPresenter,
  private val dialogFactory: DialogFactory
) {

  fun getSaveLocation(): String {
    BackgroundUtils.ensureMainThread()

    if (ChanSettings.saveLocation.isSafDirActive()) {
      return ChanSettings.saveLocation.safBaseDir.get()
    }

    return ChanSettings.saveLocation.fileApiBaseDir.get()
  }

  fun useSAFLocationPicker() {
    if (!PersistableChanState.safExplanationMessageShown.get()) {
      dialogFactory.createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.media_settings_saf_explanation_title),
        descriptionText = getString(R.string.media_settings_saf_explanation_message),
        onPositiveButtonClickListener = {
          PersistableChanState.safExplanationMessageShown.set(true)
          presenter.onSaveLocationUseSAFClicked()
        },
        cancelable = false
      )

      return
    }

    presenter.onSaveLocationUseSAFClicked()
  }

  fun showUseSAFOrOldAPIForSaveLocationDialog() {
    BackgroundUtils.ensureMainThread()

    callbacks.runWithWritePermissionsOrShowErrorToast({
      dialogFactory.createSimpleConfirmationDialog(
        context = context,
        titleTextId = R.string.media_settings_use_saf_for_save_location_dialog_title,
        descriptionTextId = R.string.media_settings_use_saf_for_save_location_dialog_message,
        positiveButtonText = getString(R.string.media_settings_use_saf_dialog_positive_button_text),
        onPositiveButtonClickListener = { presenter.onSaveLocationUseSAFClicked() },
        neutralButtonText = getString(R.string.reset),
        onNeutralButtonClickListener = {
          presenter.resetSaveLocationBaseDir()

          val defaultBaseDirFile = File(ChanSettings.saveLocation.fileApiBaseDir.get())
          if (!defaultBaseDirFile.exists() && !defaultBaseDirFile.mkdirs()) {
            callbacks.onCouldNotCreateDefaultBaseDir(defaultBaseDirFile.absolutePath)
            return@createSimpleConfirmationDialog
          }

          callbacks.updateSaveLocationViewText(ChanSettings.saveLocation.fileApiBaseDir.get())
          callbacks.onFilesBaseDirectoryReset()
        },
        negativeButtonText = getString(R.string.media_settings_use_saf_dialog_negative_button_text),
        onNegativeButtonClickListener = { onSaveLocationUseOldApiClicked() }
      )
    })
  }

  /**
   * Select a directory where saved images will be stored via the old Java File API
   */
  private fun onSaveLocationUseOldApiClicked() {
    BackgroundUtils.ensureMainThread()

    val saveLocationController = SaveLocationController(
      context,
      SaveLocationController.SaveLocationControllerMode.ImageSaveLocation,
      { dirPath -> presenter.onSaveLocationChosen(dirPath) }
    )

    callbacks.pushController(saveLocationController)
  }


  interface MediaControllerCallbacks {
    fun runWithWritePermissionsOrShowErrorToast(func: Runnable)
    fun pushController(saveLocationController: SaveLocationController)
    fun updateSaveLocationViewText(newLocation: String)
    fun presentController(loadingViewController: LoadingViewController)
    fun onFilesBaseDirectoryReset()
    fun onCouldNotCreateDefaultBaseDir(path: String)
  }

}