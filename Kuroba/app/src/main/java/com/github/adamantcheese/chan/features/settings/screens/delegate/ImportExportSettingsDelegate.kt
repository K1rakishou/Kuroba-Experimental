package com.github.adamantcheese.chan.features.settings.screens.delegate

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.presenter.ImportExportSettingsPresenter
import com.github.adamantcheese.chan.core.repository.ImportExportRepository
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.controller.LoadingViewController
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback

class ImportExportSettingsDelegate(
  private val context: Context,
  private val navigationController: NavigationController,
  private val fileChooser: FileChooser,
  private val fileManager: FileManager,
  private val databaseManager: DatabaseManager
) : ImportExportSettingsPresenter.ImportExportSettingsCallbacks {
  private val loadingViewController = LoadingViewController(context, true)
  private val presenter: ImportExportSettingsPresenter = ImportExportSettingsPresenter(this)

  fun onDestroy() {
    presenter.onDestroy()
  }

  fun onExportClicked() {
    val localThreadsLocationIsSAFBacked = ChanSettings.localThreadLocation.isSafDirActive()
    val savedFilesLocationIsSAFBacked = ChanSettings.saveLocation.isSafDirActive()

    if (localThreadsLocationIsSAFBacked || savedFilesLocationIsSAFBacked) {
      showDirectoriesWillBeResetToDefaultDialog(
        localThreadsLocationIsSAFBacked,
        savedFilesLocationIsSAFBacked
      )

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

  @Suppress("MoveLambdaOutsideParentheses")
  private fun showDirectoriesWillBeResetToDefaultDialog(
    localThreadsLocationIsSAFBacked: Boolean, savedFilesLocationIsSAFBacked: Boolean
  ) {
    check(localThreadsLocationIsSAFBacked || savedFilesLocationIsSAFBacked) {
      "Both localThreadsLocationIsSAFBacked and savedFilesLocationIsSAFBacked are false, wtf?"
    }

    val localThreadsString = if (localThreadsLocationIsSAFBacked) {
      context.getString(R.string.import_or_export_warning_local_threads_base_dir)
    } else {
      ""
    }

    val andString = if (localThreadsLocationIsSAFBacked && savedFilesLocationIsSAFBacked) {
      context.getString(R.string.import_or_export_warning_and)
    } else {
      ""
    }

    val savedFilesString = if (savedFilesLocationIsSAFBacked) {
      context.getString(R.string.import_or_export_warning_saved_files_base_dir)
    } else {
      ""
    }

    val messagePartOne = AndroidUtils.getString(
      R.string.import_or_export_warning_super_long_message_part_one,
      localThreadsString,
      andString,
      savedFilesString
    )

    var messagePartTwo: String? = ""

    if (localThreadsLocationIsSAFBacked) {
      val downloadingThreadsCount: Long = databaseManager.runTask {
        databaseManager.databaseSavedThreadManager
          .countDownloadingThreads()
          .call()
      }

      if (downloadingThreadsCount > 0) {
        messagePartTwo = context.getString(R.string.import_or_export_warning_super_long_message_part_two)
      }
    }

    val fullMessage = String.format("%s %s", messagePartOne, messagePartTwo)
    val alertDialog: AlertDialog = AlertDialog.Builder(context)
      .setTitle(context.getString(R.string.import_or_export_warning))
      .setMessage(fullMessage)
      .setPositiveButton(R.string.ok, { dialog, _ ->
        dialog.dismiss()
        showCreateNewOrOverwriteDialog()
      })
      .create()

    alertDialog.show()
  }

  /**
   * SAF is kinda horrible so it cannot be used to overwrite a file that already exist on the disk
   * (or at some network location). When trying to do so, a new file with appended "(1)" at the
   * end will appear. That's why there are two methods (one for overwriting an existing file and
   * the other one for creating a new file) instead of one that does everything.
   */
  @Suppress("MoveLambdaOutsideParentheses")
  private fun showCreateNewOrOverwriteDialog() {
    val positiveButtonId = R.string.import_or_export_dialog_positive_button_text
    val negativeButtonId = R.string.import_or_export_dialog_negative_button_text

    val alertDialog: AlertDialog = AlertDialog.Builder(context)
      .setTitle(R.string.import_or_export_dialog_title)
      .setPositiveButton(positiveButtonId, { _, _ -> overwriteExisting() })
      .setNegativeButton(negativeButtonId, { _, _ -> createNew() })
      .create()

    alertDialog.show()
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