/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.manager

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.text.parseAsHtml
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.net.update.UpdateApiRequest
import com.github.k1rakishou.chan.core.net.update.UpdateApiRequest.ReleaseUpdateApiResponse
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getFlavorType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isBetaBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isFdroidBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils.runOnMainThread
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.FlavorType
import com.github.k1rakishou.common.AndroidUtils.getAppFileProvider
import com.github.k1rakishou.common.AndroidUtils.getApplicationLabel
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.persist_state.PersistableChanState
import dagger.Lazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * Calls the update API and downloads and requests installs of APK files.
 *
 * The APK files are downloaded to the public Download directory, and the default APK install
 * screen is launched after downloading.
 */ 
class UpdateManager(
  private val context: Context,
  private val cacheHandler: Lazy<CacheHandler>,
  private val fileCacheV2: Lazy<FileCacheV2>,
  private val fileManager: Lazy<FileManager>,
  private val settingsNotificationManager: SettingsNotificationManager,
  private val fileChooser: Lazy<FileChooser>,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val dialogFactory: Lazy<DialogFactory>
) : CoroutineScope {
  private var updateDownloadDialog: ProgressDialog? = null
  private var cancelableDownload: CancelableDownload? = null

  private val job = SupervisorJob()
  private val cacheFileType = CacheFileType.Other

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + job + CoroutineName("UpdateManager")

  fun onDestroy() {
    job.cancelChildren()

    cancelableDownload?.cancel()
    cancelableDownload = null
  }

  /**
   * Runs every time onCreate is called on the StartActivity.
   */
  fun autoUpdateCheck() {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "autoUpdateCheck()")

    if (isDevBuild()) {
      Logger.d(TAG, "Updater is disabled for dev builds!")
      return
    }

    if (isFdroidBuild()) {
      Logger.d(TAG, "Updater is disabled for fdroid builds!")
      return
    }

    if (
      PersistableChanState.previousVersion.get() < BuildConfig.VERSION_CODE
      && PersistableChanState.previousVersion.get() != 0
    ) {
      onReleaseAlreadyUpdated()
      // Don't process the updater because a dialog is now already showing.
      return
    }

    if (PersistableChanState.previousDevHash.get() != BuildConfig.COMMIT_HASH) {
      onDevAlreadyUpdated()
      return
    }

    launch { runUpdateApi(false) }
  }

  fun manualUpdateCheck() {
    Logger.d(TAG, "manualUpdateCheck()")

    if (isDevBuild()) {
      Logger.d(TAG, "Updater is disabled for dev builds!")
      return
    }

    if (isFdroidBuild()) {
      Logger.d(TAG, "Updater is disabled for fdroid builds!")
      return
    }

    launch { runUpdateApi(true) }
  }

  @Suppress("ConstantConditionIf", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private suspend fun runUpdateApi(manual: Boolean) {
    BackgroundUtils.ensureBackgroundThread()
    Logger.d(TAG, "runUpdateApi() manual=$manual")

    if (PersistableChanState.hasNewApkUpdate.get()) {
      // If we noticed that there was an apk update on the previous check - show the
      // notification
      notifyNewApkUpdate()
    }

    if (!manual) {
      val lastUpdateTime = PersistableChanState.updateCheckTime.get()
      val interval = TimeUnit.DAYS.toMillis(BuildConfig.UPDATE_DELAY.toLong())
      val now = System.currentTimeMillis()
      val delta = lastUpdateTime + interval - now

      if (delta > 0) {
        return
      }

      PersistableChanState.updateCheckTime.set(now)
    }

    when (val flavorType = getFlavorType()) {
      FlavorType.Stable,
      FlavorType.Beta -> {
        val updateUrl = when (flavorType) {
          FlavorType.Stable -> BuildConfig.RELEASE_UPDATE_API_ENDPOINT
          FlavorType.Beta -> BuildConfig.BETA_UPDATE_API_ENDPOINT
          FlavorType.Dev,
          FlavorType.Fdroid -> {
            return
          }
        }

        if (flavorType == FlavorType.Stable) {
          Logger.d(TAG, "Calling update API for release ($updateUrl)")
        } else {
          Logger.d(TAG, "Calling update API for beta ($updateUrl)")
        }

        updateApk(manual, flavorType, updateUrl)
      }
      FlavorType.Fdroid,
      FlavorType.Dev -> {
        throw RuntimeException("Updater should be disabled for dev builds")
      }
    }.exhaustive
  }

  private suspend fun updateApk(manual: Boolean, flavorType: FlavorType, updateUrl: String) {
    BackgroundUtils.ensureBackgroundThread()

    val request = Request.Builder()
      .url(updateUrl)
      .get()
      .build()

    val response = UpdateApiRequest(
      request = request,
      proxiedOkHttpClient = proxiedOkHttpClient,
      isRelease = flavorType == FlavorType.Stable
    ).execute()

    coroutineScope {
      withContext(Dispatchers.Main) {
        when (response) {
          is JsonReaderRequest.JsonReaderResponse.Success -> {
            Logger.d(TAG, "ReleaseUpdateApiRequest success")

            if (!processUpdateApiResponse(response.result, manual) && manual) {
              dialogFactory.get().createSimpleInformationDialog(
                context = context,
                titleText = getString(R.string.update_none, getApplicationLabel()),
              )
            }
          }
          is JsonReaderRequest.JsonReaderResponse.ServerError -> {
            Logger.e(TAG, "Error while trying to get new release apk, status code: ${response.statusCode}")
            failedUpdate(manual, BadStatusResponseException(response.statusCode))
          }
          is JsonReaderRequest.JsonReaderResponse.UnknownServerError -> {
            Logger.e(TAG, "Unknown error while trying to get new release apk", response.error)
            failedUpdate(manual, response.error)
          }
          is JsonReaderRequest.JsonReaderResponse.ParsingError -> {
            Logger.e(TAG, "Parsing error while trying to get new release apk", response.error)
            failedUpdate(manual, response.error)
          }
        }
      }
    }
  }

  private fun processUpdateApiResponse(
    responseRelease: ReleaseUpdateApiResponse,
    manual: Boolean
  ): Boolean {
    BackgroundUtils.ensureMainThread()

    if (!BackgroundUtils.isInForeground()) {
      Logger.d(TAG, "processUpdateApiResponse() not in foreground")
      return false
    }

    val actuallyHasUpdate = responseRelease.versionCode > BuildConfig.VERSION_CODE
      || !ChanSettings.checkUpdateApkVersionCode.get()

    Logger.d(TAG, "processUpdateApiResponse() responseRelease=${responseRelease}, manual=${manual}, " +
      "actuallyHasUpdate=$actuallyHasUpdate, releaseVersionCode=${responseRelease.versionCode}, " +
      "appVersionCode=${BuildConfig.VERSION_CODE}")

    if (!actuallyHasUpdate) {
      cancelApkUpdateNotification()
      return false
    }

    // Do not spam dialogs if this is not the manual update check, use the notifications
    // instead
    if (manual) {
      val concat = responseRelease.updateTitle.isNotEmpty()

      val updateMessage = if (concat) {
        TextUtils.concat(responseRelease.updateTitle, "; ", responseRelease.body)
      } else {
        responseRelease.body!!
      }

      val dialogTitle = getApplicationLabel().toString() + " " +
        responseRelease.versionCodeString + " available"

      dialogFactory.get().createSimpleConfirmationDialog(
        context = context,
        titleText = dialogTitle,
        descriptionText = updateMessage,
        negativeButtonText = getString(R.string.update_later),
        positiveButtonText = getString(R.string.update_install),
        onPositiveButtonClickListener = { updateInstallRequested(responseRelease) }
      )
    }

    if (actuallyHasUpdate) {
      // There is an update, show the notification.
      //
      // (In case of the dev build we check whether the apk hashes differ or not beforehand,
      // so if they are the same this method won't even get called. In case of the release
      // build this method will be called in both cases so we do the check in this method)
      notifyNewApkUpdate()
    }

    return true
  }

  private fun onDevAlreadyUpdated() {
    BackgroundUtils.ensureMainThread()

    // Show toast because dev updates may happen every day (to avoid alert dialog spam)
    showToast(
      context,
      getApplicationLabel().toString() + " was updated to the latest commit."
    )

    PersistableChanState.previousDevHash.setSync(BuildConfig.COMMIT_HASH)
    cancelApkUpdateNotification()
  }

  private fun onReleaseAlreadyUpdated() {
    BackgroundUtils.ensureMainThread()

    // Show dialog because release updates are infrequent so it's fine
    val text = ("<h3>" + getApplicationLabel() + " was updated to " + BuildConfig.VERSION_NAME + "</h3>")
      .parseAsHtml()

    dialogFactory.get().createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.update_already_updated),
      descriptionText = text
    )

    // Also set the new app version to not show this message again
    PersistableChanState.previousVersion.setSync(BuildConfig.VERSION_CODE)
    cancelApkUpdateNotification()
  }

  private fun notifyNewApkUpdate() {
    PersistableChanState.hasNewApkUpdate.set(true)
    settingsNotificationManager.notify(SettingNotificationType.ApkUpdate)
  }

  private fun cancelApkUpdateNotification() {
    PersistableChanState.hasNewApkUpdate.set(false)
    settingsNotificationManager.cancel(SettingNotificationType.ApkUpdate)
  }

  @Suppress("ConstantConditionIf")
  private fun failedUpdate(manual: Boolean, error: Throwable) {
    BackgroundUtils.ensureMainThread()
    Logger.e(TAG, "failedUpdate() manual=$manual", error)

    val buildTag = if (getFlavorType() == FlavorType.Beta) {
      "beta"
    } else {
      "release"
    }

    Logger.e(TAG, "Failed to process $buildTag API call for updating")

    if (manual && BackgroundUtils.isInForeground()) {
      dialogFactory.get().createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.update_check_failed),
        descriptionText = getString(R.string.update_install_download_failed_description, error.errorMessageOrClassName())
      )
    }
  }

  /**
   * Install the APK file specified in `update`. This methods needs the storage permission.
   *
   * @param responseRelease that contains the APK file URL
   */
  private fun doUpdate(responseRelease: ReleaseUpdateApiResponse) {
    BackgroundUtils.ensureMainThread()

    updateDownloadDialog = ProgressDialog(context).apply {
      setCanceledOnTouchOutside(true)

      setOnDismissListener {
        showToast(context, "Download will continue in background.")
        updateDownloadDialog = null
      }

      max = 10000
      setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
      setProgressNumberFormat("")

      show()
      dialogFactory.get().applyColorsToDialog(this)
    }

    cancelableDownload?.cancel()
    cancelableDownload = null

    val apkUrl = responseRelease.apkURL.toString()
    cacheHandler.get().deleteCacheFileByUrl(cacheFileType, apkUrl)

    cancelableDownload = fileCacheV2.get().enqueueDownloadFileRequest(
      url = apkUrl,
      cacheFileType = cacheFileType,
      callback = object : FileCacheListener() {
        override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
          BackgroundUtils.ensureMainThread()

          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.progress =
              (updateDownloadDialog!!.max * (downloaded / total.toDouble())).toInt()
          }
        }

        override fun onSuccess(file: File) {
          Logger.d(TAG, "APK download success")
          BackgroundUtils.ensureMainThread()

          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.setOnDismissListener(null)
            updateDownloadDialog!!.dismiss()
            updateDownloadDialog = null
          }

          val fileName = getApplicationLabel().toString() +
            "_" + responseRelease.versionCodeString + ".apk"

          suggestCopyingApkToAnotherDirectory(file, fileName) {
            runOnMainThread({
              // Install from the filecache rather than downloads, as the
              // Environment.DIRECTORY_DOWNLOADS may not be "Download"
              installApk(file, responseRelease)
            }, TimeUnit.SECONDS.toMillis(1))
          }
        }

        override fun onNotFound() {
          onFail(IOException("Not found"))
        }

        override fun onFail(exception: Exception) {
          Logger.e(TAG, "APK download failed", exception)

          if (!BackgroundUtils.isInForeground()) {
            return
          }

          BackgroundUtils.ensureMainThread()
          val description = getString(
            R.string.update_install_download_failed_description,
            exception.message
          )

          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.setOnDismissListener(null)
            updateDownloadDialog!!.dismiss()
            updateDownloadDialog = null
          }

          dialogFactory.get().createSimpleInformationDialog(
            context = context,
            titleText = getString(R.string.update_install_download_failed),
            descriptionText = description
          )
        }

        override fun onCancel() {
          Logger.e(TAG, "APK download canceled")

          if (!BackgroundUtils.isInForeground()) {
            return
          }

          BackgroundUtils.ensureMainThread()
          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.setOnDismissListener(null)
            updateDownloadDialog!!.dismiss()
            updateDownloadDialog = null
          }

          dialogFactory.get().createSimpleInformationDialog(
            context = context,
            titleText = getString(R.string.update_install_download_failed),
            descriptionText = getString(R.string.update_install_download_failed_canceled)
          )
        }
      })
  }

  private fun suggestCopyingApkToAnotherDirectory(
    file: File,
    fileName: String,
    onDone: () -> Unit
  ) {
    if (!BackgroundUtils.isInForeground() || !ChanSettings.showCopyApkUpdateDialog.get()) {
      onDone.invoke()
      return
    }

    dialogFactory.get().createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.update_manager_copy_apk_title,
      descriptionTextId = R.string.update_manager_copy_apk_message,
      negativeButtonText = getString(R.string.no),
      onNegativeButtonClickListener = { onDone.invoke() },
      positiveButtonText = getString(R.string.yes),
      onPositiveButtonClickListener = {
        fileChooser.get().openCreateFileDialog(fileName, object : FileCreateCallback() {
          override fun onResult(uri: Uri) {
            onApkFilePathSelected(file, uri)
            onDone.invoke()
          }

          override fun onCancel(reason: String) {
            showToast(context, reason)
            onDone.invoke()
          }
        })
      }
    )

  }

  private fun onApkFilePathSelected(downloadedFile: File, uri: Uri) {
    val newApkFile = fileManager.get().fromUri(uri)
    if (newApkFile == null) {
      val message = getString(R.string.update_manager_could_not_convert_uri, uri.toString())
      showToast(context, message)
      return
    }

    if (!downloadedFile.exists()) {
      val message = getString(
        R.string.update_manager_input_file_does_not_exist,
        downloadedFile.absolutePath
      )

      showToast(context, message)
      return
    }

    if (!fileManager.get().exists(newApkFile)) {
      val message = getString(
        R.string.update_manager_output_file_does_not_exist,
        newApkFile.toString()
      )

      showToast(context, message)
      return
    }

    val downloadedFileRaw = fileManager.get().fromRawFile(downloadedFile)

    if (!fileManager.get().copyFileContents(downloadedFileRaw, newApkFile)) {
      val message = getString(
        R.string.update_manager_could_not_copy_apk,
        downloadedFileRaw.getFullPath(),
        newApkFile.getFullPath()
      )

      showToast(context, message)
      return
    }

    showToast(context, R.string.update_manager_apk_copied)
  }

  private fun installApk(apkFile: File, responseRelease: ReleaseUpdateApiResponse) {
    BackgroundUtils.ensureMainThread()

    if (!BackgroundUtils.isInForeground()) {
      return
    }

    cancelApkUpdateNotification()

    // First open the dialog that asks to retry and calls this method again.
    dialogFactory.get().createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.update_retry_title,
      descriptionText = getString(R.string.update_retry, getApplicationLabel()),
      negativeButtonText = getString(R.string.cancel),
      positiveButtonText = getString(R.string.update_retry_button),
      onPositiveButtonClickListener = { installApk(apkFile, responseRelease) }
    )

    try {
      val intent = if (AndroidUtils.isAndroidN()) {
        Logger.d(TAG, "installApk() AndroidN and above, apkFile=${apkFile.absolutePath}")

        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
          flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
          val apkUri = FileProvider.getUriForFile(context, getAppFileProvider(), apkFile)
          setDataAndType(apkUri, "application/vnd.android.package-archive")
        }
      } else {
        val externalFileName = "KurobaEx-${responseRelease.versionCode}.apk"

        val externalApkFile = File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
          externalFileName
        )

        if (externalApkFile.exists()) {
          externalApkFile.delete()
        }

        apkFile.copyTo(externalApkFile, overwrite = true)
        Logger.d(TAG, "installApk() AndroidM and below, apkFile=${externalApkFile.absolutePath}")

        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
          val apkUri = Uri.fromFile(externalApkFile)
          setDataAndType(apkUri, "application/vnd.android.package-archive")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
      }

      // The installer wants a content scheme from android N and up,
      // but I don't feel like implementing a content provider just for this feature.
      // Temporary change the strictmode policy while starting the intent.
      val vmPolicy = StrictMode.getVmPolicy()
      StrictMode.setVmPolicy(VmPolicy.LAX)
      openIntent(intent)
      StrictMode.setVmPolicy(vmPolicy)
    } catch (error: Throwable) {
      if (isDevBuild() || isBetaBuild()) {
        throw error
      }

      Logger.e(TAG, "installApk(${apkFile.absolutePath}) error", error)

      dialogFactory.get().createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.update_failed_to_install),
        descriptionText = getString(R.string.update_failed_to_install_description, error.errorMessageOrClassName())
      )
    }
  }

  private fun updateInstallRequested(responseRelease: ReleaseUpdateApiResponse) {
    val runtimePermissionsHelper = (context as ControllerHostActivity).runtimePermissionsHelper

    if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      doUpdate(responseRelease)
      return
    }

    runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
      if (granted) {
        doUpdate(responseRelease)
        return@requestPermission
      }

      runtimePermissionsHelper.showPermissionRequiredDialog(
        context,
        getString(R.string.update_storage_permission_required_title),
        getString(R.string.update_storage_permission_required)
      ) {
        updateInstallRequested(responseRelease)
      }
    }
  }

  companion object {
    private const val TAG = "UpdateManager"
  }

}