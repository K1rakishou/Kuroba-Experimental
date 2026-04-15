package com.github.k1rakishou.chan.features.settings.delegate

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadingDelegate
import com.github.k1rakishou.chan.features.settings.screen.SettingActions
import com.github.k1rakishou.chan.ui.controller.KurobaProgressDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat

class ImportExportSettingsDelegate(
  private val coroutineScope: CoroutineScope,
  private val appResources: AppResources,
  private val appRestarter: AppRestarter,
  private val fileChooser: FileChooser,
  private val fileManager: FileManager,
  private val dialogFactory: DialogFactory,
  private val importExportRepository: ImportExportRepository,
  private val threadDownloadingDelegate: ThreadDownloadingDelegate
) {
  suspend fun onExportClicked(context: Context, settingActions: SettingActions) {
    val dateString = BACKUP_DATE_FORMAT.print(DateTime.now())
    val exportFileName = "KurobaEx-${BuildConfig.FLAVOR}_v${BuildConfig.VERSION_CODE}_($dateString)_backup.zip"

    val uriResult = suspendCancellableCoroutine { continuation ->
      fileChooser.openCreateFileDialog(
        fileName = exportFileName,
        fileCreateCallback = object : FileCreateCallback() {
          override fun onResult(uri: Uri) {
            continuation.resumeValueSafe(Result.success(uri))
          }

          override fun onCancel(reason: String) {
            continuation.resumeValueSafe(Result.failure(ImportExportException(reason)))
          }
        }
      )
    }

    uriResult
      .onSuccess { uri -> onExportFileChosen(context, uri, settingActions) }
      .onFailure { throwable -> settingActions.showToast(throwable.errorMessageOrClassName()) }
  }

  suspend fun onImportClicked(context: Context, settingActions: SettingActions) {
    if (threadDownloadingDelegate.running) {
      dialogFactory.createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.import_export_backup_export_thread_downloader_is_running),
        descriptionText = getString(R.string.import_export_backup_export_thread_downloader_is_running_description)
      )

      return
    }

    val uriResult = suspendCancellableCoroutine { continuation ->
      fileChooser.openChooseFileDialog(
        fileChooserCallback = object : FileChooserCallback() {
          override fun onResult(uri: Uri) {
            continuation.resumeValueSafe(Result.success(uri))
          }

          override fun onCancel(reason: String) {
            continuation.resumeValueSafe(Result.failure(ImportExportException(reason)))
          }
        }
      )
    }

    uriResult
      .onSuccess { uri -> onImportFileChosen(context, uri, settingActions) }
      .onFailure { throwable -> settingActions.showToast(throwable.errorMessageOrClassName()) }
  }

  private suspend fun onExportFileChosen(context: Context, uri: Uri, settingActions: SettingActions) {
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
          val progressDialogController = KurobaProgressDialogController(
            context = context,
            params = KurobaProgressDialogController.Params.create(
              appResources = appResources,
              intermediate = true
            )
          )

          val result = try {
            settingActions.presentController(progressDialogController)

            importExportRepository.exportTo(externalFile, exportBackupOptions)
          } finally {
            progressDialogController.stopPresenting()
          }

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

    settingActions.presentController(exportBackupOptionsController)
  }

  private fun onImportFileChosen(context: Context, uri: Uri, settingActions: SettingActions) {
    val externalFile = fileManager.fromUri(uri)
    if (externalFile == null) {
      val message = "onImportClicked() fileManager.fromUri() returned null, uri = $uri"
      Logger.d(TAG, message)
      showToast(context, message, Toast.LENGTH_LONG)
      return
    }

    coroutineScope.launch {
      val progressDialogController = KurobaProgressDialogController(
        context = context,
        params = KurobaProgressDialogController.Params.create(
          appResources = appResources,
          intermediate = true
        )
      )

      val result = try {
        settingActions.presentController(progressDialogController)

        importExportRepository.importFrom(externalFile)
      } finally {
        progressDialogController.stopPresenting()
      }

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

  class ImportExportException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "ImportExportSettingsDelegate"

    private val BACKUP_DATE_FORMAT = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .toFormatter()
  }
}