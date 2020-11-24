package com.github.k1rakishou.chan.features.settings.screens.delegate

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.presenter.ImportExportSettingsPresenter
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback

class ImportExportSettingsDelegate(
  private val context: Context,
  private val navigationController: NavigationController,
  private val fileChooser: FileChooser,
  private val fileManager: FileManager,
  private val dialogFactory: DialogFactory,
  private val importExportRepository: ImportExportRepository
) : ImportExportSettingsPresenter.ImportExportSettingsCallbacks {
  private val loadingViewController = LoadingViewController(context, true)

  private val presenter by lazy {
    ImportExportSettingsPresenter(importExportRepository, this)
  }

  fun onDestroy() {
    presenter.onDestroy()
  }

  fun onExportClicked() {
    val savedFilesLocationIsSAFBacked = ChanSettings.saveLocation.isSafDirActive()

    if (savedFilesLocationIsSAFBacked) {
      showDirectoriesWillBeResetToDefaultDialog()
      return
    }

    showCreateNewOrOverwriteDialog()
  }

  fun onImportClicked() {
    fileChooser.openChooseFileDialog(object : FileChooserCallback() {
      override fun onResult(uri: Uri) {
        val externalFile = fileManager.fromUri(uri)

        if (externalFile == null) {
          val message = "onImportClicked() fileManager.fromUri() returned null, uri = $uri"
          Logger.d(TAG, message)
          showToast(context, message, Toast.LENGTH_LONG)
          return
        }
        navigationController.presentController(loadingViewController)
        presenter.doImport(externalFile)
      }

      override fun onCancel(reason: String) {
        showToast(context, reason, Toast.LENGTH_LONG)
      }
    })
  }

  fun onImportFromKurobaClicked() {
    fileChooser.openChooseFileDialog(object : FileChooserCallback() {
      override fun onResult(uri: Uri) {
        val externalFile = fileManager.fromUri(uri)
        if (externalFile == null) {
          val message = "onImportFromKurobaClicked() fileManager.fromUri() returned null, uri = $uri"
          Logger.d(TAG, message)
          showToast(context, message, Toast.LENGTH_LONG)
          return
        }

        navigationController.presentController(loadingViewController)
        presenter.doImportFromKuroba(externalFile)
      }

      override fun onCancel(reason: String) {
        showToast(context, reason, Toast.LENGTH_LONG)
      }
    })
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun showDirectoriesWillBeResetToDefaultDialog() {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.import_or_export_warning,
      descriptionTextId = R.string.import_or_export_saf_location_warning,
      onPositiveButtonClickListener = { dialog ->
        dialog.dismiss()
        showCreateNewOrOverwriteDialog()
      }
    )
  }

  /**
   * SAF is kinda horrible so it cannot be used to overwrite a file that already exist on the disk
   * (or at some network location). When trying to do so, a new file with appended "(1)" at the
   * end will appear. That's why there are two methods (one for overwriting an existing file and
   * the other one for creating a new file) instead of one that does everything.
   */
  @Suppress("MoveLambdaOutsideParentheses")
  private fun showCreateNewOrOverwriteDialog() {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.import_or_export_dialog_title,
      positiveButtonText = getString(R.string.import_or_export_dialog_positive_button_text),
      onPositiveButtonClickListener = { overwriteExisting() },
      negativeButtonText = getString(R.string.import_or_export_dialog_negative_button_text),
      onNegativeButtonClickListener = { createNew() }
    )
  }

  /**
   * Opens an existing file (any file) for overwriting with the settings.
   */
  private fun overwriteExisting() {
    fileChooser.openChooseFileDialog(object : FileChooserCallback() {
      override fun onResult(uri: Uri) {
        onFileChosen(uri, false)
      }

      override fun onCancel(reason: String) {
        showToast(context, reason, Toast.LENGTH_LONG)
      }
    })
  }

  /**
   * Creates a new file with the default name (that can be changed in the file chooser) with the
   * settings. Cannot be used for overwriting an old settings file (when trying to do so a new file
   * with appended "(1)" at the end will appear, e.g. "test (1).txt")
   */
  private fun createNew() {
    fileChooser.openCreateFileDialog(
      EXPORT_FILE_NAME,
      object : FileCreateCallback() {
        override fun onResult(uri: Uri) {
          onFileChosen(uri, true)
        }

        override fun onCancel(reason: String) {
          showToast(context, reason, Toast.LENGTH_LONG)
        }
      })
  }

  private fun onFileChosen(uri: Uri, isNewFile: Boolean) {
    // We use SAF here by default because settings importing/exporting does not depend on the
    // Kuroba default directory location. There is just no need to use old java files.
    val externalFile = fileManager.fromUri(uri)
    if (externalFile == null) {
      val message = "onFileChosen() fileManager.fromUri() returned null, uri = $uri"
      Logger.d(TAG, message)
      showToast(context, message, Toast.LENGTH_LONG)
      return
    }

    navigationController.presentController(loadingViewController)
    presenter.doExport(externalFile, isNewFile)
  }

  override fun onSuccess(importExport: ImportExportRepository.ImportExport) {
    // called on background thread
    if (context !is StartActivity) {
      return
    }

    BackgroundUtils.runOnMainThread {
      if (importExport === ImportExportRepository.ImportExport.Import) {
        context.restartApp()
      } else {
        loadingViewController.stopPresenting()
        showToast(context, R.string.successfully_exported_text, Toast.LENGTH_LONG)
      }
    }
  }

  override fun onError(message: String?) {
    BackgroundUtils.runOnMainThread {
      loadingViewController.stopPresenting()
      showToast(context, message, Toast.LENGTH_LONG)
    }
  }

  companion object {
    private const val TAG = "ImportExportSettingsDelegate"
    val EXPORT_FILE_NAME = AndroidUtils.getApplicationLabel().toString() + "_exported_settings.json"
  }
}