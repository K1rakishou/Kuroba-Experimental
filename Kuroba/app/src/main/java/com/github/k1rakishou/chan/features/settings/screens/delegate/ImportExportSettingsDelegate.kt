package com.github.k1rakishou.chan.features.settings.screens.delegate

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat

class ImportExportSettingsDelegate(
  private val context: Context,
  private val coroutineScope: CoroutineScope,
  private val navigationController: NavigationController,
  private val appRestarter: AppRestarter,
  private val fileChooser: FileChooser,
  private val fileManager: FileManager,
  private val dialogFactory: DialogFactory,
  private val importExportRepository: ImportExportRepository,
  private val threadDownloadingDelegate: ThreadDownloadingDelegate
) {
  private val loadingViewController = LoadingViewController(context, true)

  fun onExportClicked() {
    val dateString = BACKUP_DATE_FORMAT.print(DateTime.now())
    val exportFileName = "KurobaEx_v${BuildConfig.VERSION_CODE}_($dateString)_backup.zip"

    fileChooser.openCreateFileDialog(
      exportFileName,
      object : FileCreateCallback() {
        override fun onResult(uri: Uri) {
          onExportFileChosen(uri)
        }

        override fun onCancel(reason: String) {
          showToast(context, reason, Toast.LENGTH_LONG)
        }
      })
  }

  fun onImportClicked() {
    if (threadDownloadingDelegate.running) {
      dialogFactory.createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.import_export_backup_export_thread_downloader_is_running),
        descriptionText = getString(R.string.import_export_backup_export_thread_downloader_is_running_description)
      )

      return
    }

    fileChooser.openChooseFileDialog(object : FileChooserCallback() {
      override fun onResult(uri: Uri) {
        onImportFileChosen(uri)
      }

      override fun onCancel(reason: String) {
        showToast(context, reason, Toast.LENGTH_LONG)
      }
    })
  }

  fun onImportFromKurobaClicked() {
    fileChooser.openChooseFileDialog(object : FileChooserCallback() {
      override fun onResult(uri: Uri) {
        onImportFromKurobaFileChosen(uri)
      }

      override fun onCancel(reason: String) {
        showToast(context, reason, Toast.LENGTH_LONG)
      }
    })
  }

  private fun onExportFileChosen(uri: Uri) {
    // We use SAF here by default because settings importing/exporting does not depend on the
    // Kuroba default directory location. There is just no need to use old java files.
    val externalFile = fileManager.fromUri(uri)
    if (externalFile == null) {
      val message = "onFileChosen() fileManager.fromUri() returned null, uri = $uri"
      Logger.d(TAG, message)
      showToast(context, message, Toast.LENGTH_LONG)
      return
    }

    val exportBackupOptionsController = ExportBackupOptionsController(
      context = context,
      onOptionsSelected = { exportBackupOptions ->
        coroutineScope.launch {
          navigationController.presentController(loadingViewController)

          val result = withContext(Dispatchers.Default) {
            importExportRepository.exportTo(externalFile, exportBackupOptions)
          }

          loadingViewController.stopPresenting()

          when (result) {
            is ModularResult.Error -> {
              Logger.e(TAG, "Export error", result.error)

              dialogFactory.createSimpleInformationDialog(
                context = context,
                titleText = getString(R.string.import_export_backup_export_error),
                descriptionText = getString(
                  R.string.import_export_backup_export_error_description,
                  result.error.errorMessageOrClassName()
                )
              )
            }
            is ModularResult.Value -> {
              showToast(context, R.string.import_export_backup_export_success)
            }
          }
        }
      }
    )

    navigationController.presentController(exportBackupOptionsController)
  }

  private fun onImportFileChosen(uri: Uri) {
    val externalFile = fileManager.fromUri(uri)
    if (externalFile == null) {
      val message = "onImportClicked() fileManager.fromUri() returned null, uri = $uri"
      Logger.d(TAG, message)
      showToast(context, message, Toast.LENGTH_LONG)
      return
    }

    coroutineScope.launch {
      navigationController.presentController(loadingViewController)

      val result = withContext(Dispatchers.Default) {
        importExportRepository.importFrom(externalFile)
      }

      loadingViewController.stopPresenting()

      when (result) {
        is ModularResult.Error -> {
          Logger.e(TAG, "Import error", result.error)
          showToast(context, getString(R.string.import_export_backup_import_error, result.error))
        }
        is ModularResult.Value -> {
          dialogFactory.createSimpleInformationDialog(
            context = context,
            titleText = getString(R.string.import_export_backup_import_success),
            descriptionText = getString(R.string.import_export_backup_import_success_description),
            onDismissListener = { appRestarter.restart() }
          )
        }
      }
    }
  }

  private fun onImportFromKurobaFileChosen(uri: Uri) {
    val externalFile = fileManager.fromUri(uri)
    if (externalFile == null) {
      val message = "onImportFromKurobaFileChosen() fileManager.fromUri() returned null, uri = $uri"
      Logger.d(TAG, message)
      showToast(context, message, Toast.LENGTH_LONG)
      return
    }

    coroutineScope.launch {
      navigationController.presentController(loadingViewController)

      val result = withContext(Dispatchers.Default) {
        importExportRepository.importFromKuroba(externalFile)
      }

      loadingViewController.stopPresenting()

      when (result) {
        is ModularResult.Error -> {
          Logger.e(TAG, "Import from Kuroba error", result.error)

          dialogFactory.createSimpleInformationDialog(
            context = context,
            titleText = getString(R.string.import_export_backup_import_from_kuroba_error),
            descriptionText = getString(
              R.string.import_export_backup_import_from_kuroba_error_description,
              result.error.errorMessageOrClassName()
            )
          )
        }
        is ModularResult.Value -> {
          dialogFactory.createSimpleInformationDialog(
            context = context,
            titleText = getString(R.string.import_export_backup_import_from_kuroba_success),
            descriptionText = getString(R.string.import_export_backup_import_success_description),
            onDismissListener = { appRestarter.restart() }
          )
        }
      }
    }
  }

  companion object {
    private const val TAG = "ImportExportSettingsDelegate"

    private val BACKUP_DATE_FORMAT = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .toFormatter()
  }
}