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
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.net.update.UpdateApiRequest
import com.github.k1rakishou.chan.core.net.update.UpdateApiRequest.ReleaseUpdateApiResponse
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper.PermissionRequiredDialogCallback
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getFlavorType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isBetaBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isFdroidBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isStableBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils.runOnMainThread
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.FlavorType
import com.github.k1rakishou.common.AndroidUtils.getAppFileProvider
import com.github.k1rakishou.common.AndroidUtils.getApplicationLabel
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.downloadIntoFile
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.persist_state.ApkUpdateInfo
import com.github.k1rakishou.persist_state.ApkUpdateInfoJson
import com.github.k1rakishou.persist_state.PersistableChanState
import dagger.Lazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
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
  private val fileManager: Lazy<FileManager>,
  private val settingsNotificationManager: SettingsNotificationManager,
  private val fileChooser: Lazy<FileChooser>,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val dialogFactory: Lazy<DialogFactory>
) : CoroutineScope {
  private var updateDownloadDialog: ProgressDialog? = null

  private val job = SupervisorJob()
  private val cacheFileType = CacheFileType.Other

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main + job + CoroutineName("UpdateManager")

  fun onDestroy() {
    job.cancelChildren()
  }

  /**
   * Runs every time onCreate is called on the StartActivity.
   */
  fun autoUpdateCheck() {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "autoUpdateCheck()")

    if (isDevBuild()) {
      Logger.d(TAG, "autoUpdateCheck() Updater is disabled for dev builds!")
      return
    }

    if (isFdroidBuild()) {
      Logger.d(TAG, "autoUpdateCheck() Updater is disabled for fdroid builds!")
      return
    }

    val apkUpdateInfo = getAndResetApkUpdateInfo()

    Logger.d(TAG, "autoUpdateCheck() " +
            "isStableBuild(): ${isStableBuild()}, " +
            "isBetaBuild(): ${isBetaBuild()}, " +
            "apkUpdateInfo: ${apkUpdateInfo}")

    if (isStableBuild() && apkUpdateInfo != null) {
      Logger.d(TAG, "autoUpdateCheck() isOnLatestRelease()")
      onReleaseAlreadyUpdated(apkUpdateInfo)

      // Don't process the updater because a dialog is now already showing.
      return
    }

    if (isBetaBuild() && apkUpdateInfo != null) {
      Logger.d(TAG, "autoUpdateCheck() isOnLatestBeta()")
      onBetaAlreadyUpdated(apkUpdateInfo)

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

  private suspend fun runUpdateApi(manual: Boolean) {
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

            processUpdateApiResponse(
              responseRelease = response.result,
              manual = manual,
              isRelease = flavorType == FlavorType.Stable
            )
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
    manual: Boolean,
    isRelease: Boolean
  ) {
    if (!BackgroundUtils.isInForeground()) {
      Logger.d(TAG, "processUpdateApiResponse() not in foreground")
      return
    }

    val continueWithUpdate = when {
      !ChanSettings.checkUpdateApkVersionCode.get() -> {
        Logger.d(TAG, "processUpdateApiResponse() checkUpdateApkVersionCode is false")
        true
      }
      isRelease -> canContinueReleaseUpdate(responseRelease)
      !isRelease -> canContinueBetaUpdate(responseRelease)
      else -> false
    }

    Logger.d(TAG,
      "processUpdateApiResponse() " +
              "manual: ${manual}, " +
              "actuallyHasUpdate: $continueWithUpdate, " +
              "releaseVersionCode: ${responseRelease.versionCode}, " +
              "releaseBuildNumber: ${responseRelease.buildNumber}, " +
              "appVersionCode=${BuildConfig.VERSION_CODE}"
    )

    Logger.d(TAG, "processUpdateApiResponse() responseRelease=${responseRelease}")

    if (!continueWithUpdate) {
      cancelApkUpdateNotification()

      if (manual) {
        dialogFactory.get().createSimpleInformationDialog(
          context = context,
          titleText = getString(R.string.update_none, getApplicationLabel()),
        )
      }

      return
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
        onPositiveButtonClickListener = {
          launch {
            updateInstallRequested(
              responseRelease = responseRelease,
              onUpdateClicked = {
                val apkUpdateInfoJson = ApkUpdateInfoJson(
                  versionCode = responseRelease.versionCode,
                  buildNumber = responseRelease.buildNumber,
                  versionName = responseRelease.versionCodeString
                )

                Logger.d(TAG, "processUpdateApiResponse() onUpdateClicked() updating apkUpdateInfoJson with ${apkUpdateInfoJson}")
                PersistableChanState.apkUpdateInfoJson.setSync(apkUpdateInfoJson)
              }
            )
          }
        }
      )
    } else {
      // There is an update, show the notification.
      //
      // (In case of the dev build we check whether the apk hashes differ or not beforehand,
      // so if they are the same this method won't even get called. In case of the release
      // build this method will be called in both cases so we do the check in this method)
      notifyNewApkUpdate()
    }
  }

  private fun onBetaAlreadyUpdated(apkUpdateInfo: ApkUpdateInfo) {
    BackgroundUtils.ensureMainThread()

    val toastMessage = if (apkUpdateInfo.versionName.isNotNullNorBlank()) {
      "${getApplicationLabel()} was updated to the latest version: ${apkUpdateInfo.versionName}."
    } else {
      "${getApplicationLabel()} was updated to the latest version."
    }

    showToast(context, toastMessage)

    PersistableChanState.previousDevHash.setSync(BuildConfig.COMMIT_HASH)
    PersistableChanState.previousBuildNumber.setSync(apkUpdateInfo.buildNumber)
    cancelApkUpdateNotification()
  }

  private fun onReleaseAlreadyUpdated(apkUpdateInfo: ApkUpdateInfo) {
    BackgroundUtils.ensureMainThread()

    val text = if (apkUpdateInfo.versionName.isNotNullNorBlank()) {
      "<h3> ${getApplicationLabel()} was updated to ${apkUpdateInfo.versionName}</h3>"
        .parseAsHtml()
    } else {
      "<h3> ${getApplicationLabel()} was updated to the latest version</h3>"
        .parseAsHtml()
    }

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

  private fun failedUpdate(manual: Boolean, error: Throwable) {
    Logger.e(TAG, "failedUpdate() manual=$manual", error)

    val buildTag = if (getFlavorType() == FlavorType.Beta) {
      "beta"
    } else {
      "release"
    }

    val manualUpdateUrl = if (getFlavorType() == FlavorType.Beta) {
      "https://github.com/K1rakishou/Kuroba-Experimental-beta/releases/latest"
    } else {
      "https://github.com/K1rakishou/Kuroba-Experimental/releases/latest"
    }

    Logger.e(TAG, "Failed to process $buildTag API call for updating")

    if (manual && BackgroundUtils.isInForeground()) {
      dialogFactory.get().createSimpleInformationDialog(
        context = context,
        titleText = getString(R.string.update_check_failed),
        descriptionText = getString(
          R.string.update_install_download_failed_description,
          error.errorMessageOrClassName(),
          manualUpdateUrl
        )
      )
    }
  }

  /**
   * Install the APK file specified in `update`. This methods needs the storage permission.
   *
   * @param responseRelease that contains the APK file URL
   */
  private suspend fun doUpdate(
    responseRelease: ReleaseUpdateApiResponse,
    onUpdateClicked: () -> Unit
  ) {
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

    val apkUrl = responseRelease.apkURL.toString()
    cacheHandler.get().deleteCacheFileByUrl(cacheFileType, apkUrl)

    val apkFile = cacheHandler.get().createTemptFile()
    val request = Request.Builder().url(apkUrl).get().build()

    val downloadFileResult = proxiedOkHttpClient.get().okHttpClient().downloadIntoFile(
      request = request,
      outputFile = apkFile,
      onProgress = { percent ->
        updateDownloadDialog?.let { dialog ->
          dialog.progress = (dialog.max * percent).toInt()
        }
      }
    ).finally {
      updateDownloadDialog?.let { dialog ->
        dialog.setOnDismissListener(null)
        dialog.dismiss()
      }
      updateDownloadDialog = null
    }

    when (downloadFileResult) {
      is ModularResult.Error -> {
        val exception = downloadFileResult.error
        Logger.e(TAG, "APK download failed", exception)

        val description = getString(
          R.string.update_install_download_failed_description,
          exception.message
        )

        dialogFactory.get().createSimpleInformationDialog(
          context = context,
          titleText = getString(R.string.update_install_download_failed),
          descriptionText = description
        )
      }
      is ModularResult.Value -> {
        Logger.d(TAG, "APK download success")
        val fileName = getApplicationLabel().toString() + "_" + responseRelease.versionCodeString + ".apk"

        suggestCopyingApkToAnotherDirectory(apkFile, fileName) {
          runOnMainThread({
            installApk(apkFile, responseRelease, onUpdateClicked)
          }, TimeUnit.SECONDS.toMillis(1))
        }
      }
    }
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

  private fun installApk(apkFile: File, responseRelease: ReleaseUpdateApiResponse, onUpdateClicked: () -> Unit) {
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
      onPositiveButtonClickListener = { installApk(apkFile, responseRelease, onUpdateClicked) }
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

      onUpdateClicked()
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

  private enum class RequestPermissionResult {
    PermissionGranted,
    RetryPermissionRequest,
    Canceled
  }

  private suspend fun updateInstallRequested(
    responseRelease: ReleaseUpdateApiResponse,
    onUpdateClicked: () -> Unit
  ) {
    if (AndroidUtils.isAndroid13()) {
      // Can't request WRITE_EXTERNAL_STORAGE on API 33+
      doUpdate(responseRelease, onUpdateClicked)
      return
    }

    val runtimePermissionsHelper = (context as ControllerHostActivity).runtimePermissionsHelper

    if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      doUpdate(responseRelease, onUpdateClicked)
      return
    }

    val requestPermissionResult = suspendCancellableCoroutine<RequestPermissionResult> { continuation ->
      runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
        if (granted) {
          continuation.resumeValueSafe(RequestPermissionResult.PermissionGranted)
          return@requestPermission
        }

        runtimePermissionsHelper.showPermissionRequiredDialog(
          context,
          getString(R.string.update_storage_permission_required_title),
          getString(R.string.update_storage_permission_required),
          object : PermissionRequiredDialogCallback {
            override fun retryPermissionRequest() {
              continuation.resumeValueSafe(RequestPermissionResult.RetryPermissionRequest)
            }

            override fun onDismissed() {
              continuation.resumeValueSafe(RequestPermissionResult.Canceled)
            }
          }
        )
      }
    }

    when (requestPermissionResult) {
      RequestPermissionResult.PermissionGranted -> {
        doUpdate(responseRelease, onUpdateClicked)
      }
      RequestPermissionResult.RetryPermissionRequest -> {
        updateInstallRequested(responseRelease, onUpdateClicked)
      }
      RequestPermissionResult.Canceled -> {
        return
      }
    }
  }

  private fun getAndResetApkUpdateInfo(): ApkUpdateInfo? {
    val apkUpdateInfo = PersistableChanState.apkUpdateInfoJson.get().let { apkUpdateInfoJson ->
      val versionCode = apkUpdateInfoJson.versionCode
        ?.takeIf { it >= 0L }
        ?: return@let null
      val buildNumber = apkUpdateInfoJson.buildNumber
        ?.takeIf { it >= 0L }
        ?: return@let null
      val versionName = apkUpdateInfoJson.versionName

      return@let ApkUpdateInfo(versionCode, buildNumber, versionName)
    }

    PersistableChanState.apkUpdateInfoJson.setSync(ApkUpdateInfoJson())
    return apkUpdateInfo
  }

  private fun canContinueBetaUpdate(responseRelease: ReleaseUpdateApiResponse): Boolean {
    Logger.debug(TAG) {
      "canContinueBetaUpdate() " +
      "responseRelease.versionCode: ${responseRelease.versionCode}," +
      "BuildConfig.VERSION_CODE: ${BuildConfig.VERSION_CODE}, " +
      "responseRelease.buildNumber: ${responseRelease.buildNumber}, " +
      "PersistableChanState.previousBuildNumber: ${PersistableChanState.previousBuildNumber.get()}"
    }

    if (responseRelease.versionCode < BuildConfig.VERSION_CODE.toLong()) {
      Logger.debug(TAG) { "canContinueBetaUpdate() responseRelease.versionCode < BuildConfig.VERSION_CODE.toLong()" }
      // Do not update if release's version code is less than ours
      return false
    }

    if (responseRelease.versionCode > BuildConfig.VERSION_CODE) {
      Logger.debug(TAG) { "canContinueBetaUpdate() responseRelease.versionCode > BuildConfig.VERSION_CODE.toLong()" }
      // Always update if the release's version code is greater than ours
      return true
    }

    // If they are the same then check the build numbers
    val buildNumberIsGreater = responseRelease.buildNumber > PersistableChanState.previousBuildNumber.get()
    Logger.debug(TAG) { "canContinueBetaUpdate() responseRelease.buildNumber > PersistableChanState.previousBuildNumber.get()" }

    return buildNumberIsGreater
  }

  private fun canContinueReleaseUpdate(responseRelease: ReleaseUpdateApiResponse): Boolean {
    Logger.debug(TAG) {
      "canContinueReleaseUpdate() responseRelease.versionCode: ${responseRelease.versionCode}, " +
      "BuildConfig.VERSION_CODE: ${BuildConfig.VERSION_CODE}"
    }

    return responseRelease.versionCode > BuildConfig.VERSION_CODE
  }

  companion object {
    private const val TAG = "UpdateManager"
  }

}