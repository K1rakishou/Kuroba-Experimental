package com.github.k1rakishou.chan.core.manager.update

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import androidx.core.content.FileProvider
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.KurobaSystemNotifications
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.net.update.UpdateApiRequest
import com.github.k1rakishou.chan.core.net.update.UpdateApiRequest.ApkReleaseInfo
import com.github.k1rakishou.chan.core.usecase.LoadChangelogUseCase
import com.github.k1rakishou.chan.ui.controller.KurobaProgressDialogController
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.settings.SettingNotification
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.downloadIntoFile
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.ApkUpdateInfoJson
import com.squareup.moshi.Moshi
import dagger.Lazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Calls the update API and downloads and requests installs of APK files.
 *
 * The APK files are downloaded to the public Download directory, and the default APK install
 * screen is launched after downloading.
 */
class KurobaAppUpdateManager(
  private val context: Context,
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val moshi: Moshi,
  private val settingsNotificationManager: SettingsNotificationManager,
  private val kurobaSystemNotifications: KurobaSystemNotifications,
  private val loadChangelogUseCaseLazy: Lazy<LoadChangelogUseCase>,
  private val cacheHandlerLazy: Lazy<CacheHandler>,
  private val proxiedOkHttpClientLazy: Lazy<ProxiedOkHttpClient>,
  private val dialogFactoryLazy: Lazy<DialogFactory>
) {
  private val cacheHandler: CacheHandler
    get() = cacheHandlerLazy.get()
  private val proxiedOkHttpClient: ProxiedOkHttpClient
    get() = proxiedOkHttpClientLazy.get()
  private val dialogFactory: DialogFactory
    get() = dialogFactoryLazy.get()

  private val cacheFileType = CacheFileType.Other

  private val coroutineScope = KurobaCoroutineScope(
    coroutineName = CoroutineName("UpdateManager")
  )

  fun onDestroy() {
    coroutineScope.cancelChildren()
  }

  /**
   * Runs every time onCreate is called on the StartActivity.
   */
  fun autoUpdateCheck() {
    coroutineScope.launch {
      Logger.d(TAG, "autoUpdateCheck()")

      if (AppModuleAndroidUtils.isDevBuild) {
        Logger.d(TAG, "autoUpdateCheck() Updater is disabled for dev builds!")
        return@launch
      }

      if (AppModuleAndroidUtils.isFdroidBuild) {
        Logger.d(TAG, "autoUpdateCheck() Updater is disabled for fdroid builds!")
        return@launch
      }

      val apkUpdateInfo = getAndResetApkUpdateInfo()

      Logger.d(TAG, "autoUpdateCheck() " +
        "isStableBuild: ${AppModuleAndroidUtils.isStableBuild}, " +
        "isBetaBuild: ${AppModuleAndroidUtils.isBetaBuild}, " +
        "apkUpdateInfo: ${apkUpdateInfo}")

      if (AppModuleAndroidUtils.isStableBuild && apkUpdateInfo != null) {
        Logger.d(TAG, "autoUpdateCheck() isOnLatestRelease()")
        onReleaseAlreadyUpdated(apkUpdateInfo)

        // Don't process the updater because a dialog is now already showing.
        return@launch
      }

      if (AppModuleAndroidUtils.isBetaBuild && apkUpdateInfo != null) {
        Logger.d(TAG, "autoUpdateCheck() isOnLatestBeta()")
        onBetaAlreadyUpdated(apkUpdateInfo)

        return@launch
      }

      runUpdateApi(false)
    }
  }

  fun manualUpdateCheck() {
    coroutineScope.launch {
      Logger.d(TAG, "manualUpdateCheck()")

      if (AppModuleAndroidUtils.isDevBuild) {
        Logger.d(TAG, "Updater is disabled for dev builds!")
        return@launch
      }

      if (AppModuleAndroidUtils.isFdroidBuild) {
        Logger.d(TAG, "Updater is disabled for fdroid builds!")
        return@launch
      }

      runUpdateApi(true)
    }
  }

  private suspend fun runUpdateApi(manual: Boolean) {
    Logger.d(TAG, "runUpdateApi() manual=$manual")

    if (kurobaSettings.internal.hasNewApkUpdate.read()) {
      // If we noticed that there was an apk update on the previous check - show the
      // notification
      notifyNewApkUpdate(apkReleaseInfo = null)
    }

    if (!manual) {
      val lastUpdateTime = kurobaSettings.internal.updateCheckTime.read()
      val interval = TimeUnit.DAYS.toMillis(1)
      val now = System.currentTimeMillis()
      val delta = lastUpdateTime + interval - now

      if (delta > 0) {
        return
      }

      kurobaSettings.internal.updateCheckTime.write(now)
    }

    val usePrereleaseBuilds = kurobaSettings.application.usePrereleaseBuilds.read()
    Logger.d(TAG, "Calling update API (usePrereleaseBuilds: ${usePrereleaseBuilds})")

    val apkUpdateInfoResult = UpdateApiRequest(
      proxiedOkHttpClient = proxiedOkHttpClient,
      loadChangelogUseCase = loadChangelogUseCaseLazy.get(),
      moshi = moshi,
      canUpdateToPrerelease = usePrereleaseBuilds
    ).execute()

    withContext(Dispatchers.Main) {
      when (apkUpdateInfoResult) {
        is ModularResult.Error<*> -> {
          Logger.error(TAG, apkUpdateInfoResult.error) { "Failed to check for updates" }
          failedUpdate(manual, apkUpdateInfoResult.error)
        }
        is ModularResult.Value<ApkReleaseInfo> -> {
          Logger.d(TAG, "ReleaseUpdateApiRequest success")

          processUpdateApiResponse(
            apkReleaseInfo = apkUpdateInfoResult.value,
            manual = manual,
            usePrereleaseBuilds = usePrereleaseBuilds
          )
        }
      }
    }
  }

  private suspend fun processUpdateApiResponse(
    apkReleaseInfo: UpdateApiRequest.ApkReleaseInfo,
    manual: Boolean,
    usePrereleaseBuilds: Boolean
  ) {
    val continueWithUpdate = run {
      if (!kurobaSettings.application.checkUpdateApkVersionCode.read()) {
        Logger.d(TAG, "processUpdateApiResponse() checkUpdateApkVersionCode is false")
        return@run true
      }

      return@run when (val versionCode = apkReleaseInfo.versionCode) {
        is UpdateApiRequest.VersionCode.Beta -> canContinueBetaUpdate(versionCode)
        is UpdateApiRequest.VersionCode.Release -> canContinueReleaseUpdate(versionCode)
      }
    }

    Logger.d(TAG,
      "processUpdateApiResponse() " +
              "manual: ${manual}, " +
              "usePrereleaseBuilds: ${usePrereleaseBuilds}, " +
              "continueWithUpdate: ${continueWithUpdate}, " +
              "tagName: ${apkReleaseInfo.tagName}, " +
              "versionCode: ${apkReleaseInfo.versionCode}, " +
              "prerelease: ${apkReleaseInfo.prerelease}, " +
              "apkURL: ${apkReleaseInfo.apkURL}, " +
              "currentVersionName: ${BuildConfig.VERSION_NAME}"
    )

    Logger.d(TAG, "processUpdateApiResponse() apkReleaseInfo=${apkReleaseInfo}")

    if (!continueWithUpdate) {
      cancelApkUpdateNotification()

      if (manual) {
        dialogFactory.showDialog(
          context = context,
          params = KurobaComposeDialogController.informationDialog(
            title = KurobaComposeDialogController.Text.String(
              appResources.string(R.string.update_none, AndroidUtils.applicationLabel)
            )
          )
        )
      }

      return
    }

    kurobaSettings.internal.hasNewApkUpdate.write(true)

    // Do not spam dialogs if this is not the manual update check, use the notifications
    // instead
    if (!manual) {
      // There is an update, show the notification.
      //
      // (In case of the dev build we check whether the apk hashes differ or not beforehand,
      // so if they are the same this method won't even get called. In case of the release
      // build this method will be called in both cases so we do the check in this method)
      notifyNewApkUpdate(apkReleaseInfo)
      return
    }

    val dialogTitle = "${AndroidUtils.applicationLabel} ${apkReleaseInfo.tagName} available"
    val dialogDescription = apkReleaseInfo.releaseDescription

    val installClicked = suspendCancellableCoroutine { continuation ->
      dialogFactory.showDialog(
        context = context,
        params = KurobaComposeDialogController.confirmationDialog(
          title = KurobaComposeDialogController.Text.String(dialogTitle),
          description = KurobaComposeDialogController.Text.String(dialogDescription),
          negativeButton = KurobaComposeDialogController.DialogButton(R.string.update_later),
          positionButton = KurobaComposeDialogController.PositiveDialogButton(
            buttonText = R.string.update_install,
            isActionDangerous = true,
            onClick = { continuation.resumeValueSafe(true) }
          )
        ),
        onDismissListener = { continuation.resumeValueSafe(false) }
      )
    }

    if (!installClicked) {
      return
    }

    updateInstallRequested(
      responseRelease = apkReleaseInfo,
      onUpdateClicked = {
        val buildNumberFromReleaseTag = apkReleaseInfo.versionCode.buildNumber()

        val apkUpdateInfoJson = ApkUpdateInfoJson(
          versionCode = apkReleaseInfo.versionCode.code,
          buildNumber = buildNumberFromReleaseTag,
          versionName = apkReleaseInfo.tagName
        )

        Logger.debug(TAG) {
          "processUpdateApiResponse() onUpdateClicked() updating apkUpdateInfoJson with ${apkUpdateInfoJson}"
        }

        kurobaSettings.internal.apkUpdateInfoJson.writeBlocking(apkUpdateInfoJson)
        kurobaSettings.internal.previousBuildNumber.writeBlocking(buildNumberFromReleaseTag)
      }
    )
  }

  private suspend fun onBetaAlreadyUpdated(apkUpdateInfo: ApkUpdateInfoJson) {
    BackgroundUtils.ensureMainThread()

    val toastMessage = if (apkUpdateInfo.versionName.isNotNullNorBlank()) {
      "${AndroidUtils.applicationLabel} was updated to ${apkUpdateInfo.versionName}."
    } else {
      "${AndroidUtils.applicationLabel} was updated to the latest version."
    }

    AppModuleAndroidUtils.showToast(context, toastMessage)

    kurobaSettings.internal.previousBuildNumber.write(apkUpdateInfo.buildNumber)
    cancelApkUpdateNotification()
  }

  private suspend fun onReleaseAlreadyUpdated(apkUpdateInfo: ApkUpdateInfoJson) {
    BackgroundUtils.ensureMainThread()

    val toastMessage = if (apkUpdateInfo.versionName.isNotNullNorBlank()) {
      "${AndroidUtils.applicationLabel} was updated to ${apkUpdateInfo.versionName}"
    } else {
      "${AndroidUtils.applicationLabel} was updated to the latest version"
    }

    AppModuleAndroidUtils.showToast(context, toastMessage)

    // Reset previous build number
    kurobaSettings.internal.previousBuildNumber.write(-1L)

    cancelApkUpdateNotification()
  }

  private suspend fun notifyNewApkUpdate(apkReleaseInfo: UpdateApiRequest.ApkReleaseInfo?) {
    kurobaSettings.internal.hasNewApkUpdate.write(true)
    settingsNotificationManager.notify(SettingNotification.ApkUpdate)

    if (apkReleaseInfo == null) {
      return
    }

    val versionCode = apkReleaseInfo.versionCode.code
    val buildNumber = apkReleaseInfo.versionCode.buildNumber()

    val notificationContent = buildString {
      append(appResources.string(R.string.update_application_update_available_description))
      append(" ")

      if (versionCode > 0) {
        append("(")
        append("v${versionCode}")
        if (buildNumber >= 0) {
          append(".${buildNumber}")
        }
        if (apkReleaseInfo.prerelease) {
          append("-beta")
        }
        append(")")
      } else {
        append("(Unknown)")
      }
    }

    kurobaSystemNotifications.showNotification(
      notificationData = KurobaSystemNotifications.NotificationData(
        id = NotificationConstants.Generic.Ids.NewAppVersionAvailable.name,
        priority = KurobaSystemNotifications.NotificationData.Priority.High,
        largeIcon = KurobaSystemNotifications.NotificationData.LargeIcon.RemoteUrl(
          url = (AppConstants.RESOURCES_ENDPOINT + "ic_launcher_release_round.png").toHttpUrl()
        ),
        autoCancel = false,
        style = KurobaSystemNotifications.NotificationData.Style.Default(
          title = appResources.string(R.string.update_application_update_available),
          content = notificationContent
        ),
      )
    )
  }

  private suspend fun cancelApkUpdateNotification() {
    kurobaSettings.internal.hasNewApkUpdate.write(false)
    settingsNotificationManager.dismiss(SettingNotification.ApkUpdate)
  }

  private fun failedUpdate(manual: Boolean, error: Throwable) {
    Logger.e(TAG, "failedUpdate() manual=$manual, error: ${error.errorMessageOrClassName()}")
    val manualUpdateUrl = "https://github.com/K1rakishou/Kuroba-Experimental/releases/latest"

    if (manual) {
      dialogFactory.showDialog(
        context = context,
        params = KurobaComposeDialogController.informationDialog(
          title = KurobaComposeDialogController.Text.String(
            appResources.string(R.string.update_check_failed)
          ),
          description = KurobaComposeDialogController.Text.String(
            appResources.string(
              R.string.update_install_download_failed_description,
              error.errorMessageOrClassName(),
              manualUpdateUrl
            )
          )
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
    responseRelease: UpdateApiRequest.ApkReleaseInfo,
    onUpdateClicked: () -> Unit
  ) {
    BackgroundUtils.ensureMainThread()

    val updateDownloadDialog = dialogFactory.showProgressDialog(
      KurobaProgressDialogController(
        context = context,
        params = KurobaProgressDialogController.Params.create(
          appResources = appResources,
          cancelable = true,
          intermediate = false,
          horizontal = true
        )
      ).withCancellation {
        AppModuleAndroidUtils.showToast(context, "Download will continue in background.")
      }
    )

    val apkUrl = responseRelease.apkURL.toString()
    cacheHandler.deleteCacheFileByUrl(cacheFileType, apkUrl)

    val apkFile = cacheHandler.createTemptFile()
    val request = Request.Builder().url(apkUrl).get().build()

    val downloadFileResult = proxiedOkHttpClient.okHttpClient().downloadIntoFile(
      request = request,
      outputFile = apkFile,
      onProgress = { progressEvent -> updateDownloadDialog.updateProgress(progressEvent.progress) }
    ).finally {
      updateDownloadDialog.close()
    }

    when (downloadFileResult) {
      is ModularResult.Error -> {
        val exception = downloadFileResult.error
        Logger.e(TAG, "APK download failed", exception)

        val title = appResources.string(R.string.update_install_download_failed)
        val description = appResources.string(
          R.string.update_install_download_failed_description,
          exception.message ?: "No error message"
        )

        dialogFactory.showDialog(
          context = context,
          params = KurobaComposeDialogController.informationDialog(
            title = KurobaComposeDialogController.Text.String(title),
            description = KurobaComposeDialogController.Text.String(description)
          )
        )
      }
      is ModularResult.Value -> {
        Logger.d(TAG, "APK download success")

        delay(1000)
        installApk(apkFile, responseRelease, onUpdateClicked)
      }
    }
  }

  private suspend fun installApk(
    apkFile: File,
    responseRelease: UpdateApiRequest.ApkReleaseInfo,
    onUpdateClicked: () -> Unit
  ) {
    BackgroundUtils.ensureMainThread()

    cancelApkUpdateNotification()

    try {
      val intent = if (AndroidUtils.isAndroidN) {
        Logger.d(TAG, "installApk() AndroidN and above, apkFile=${apkFile.absolutePath}")

        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
          flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
          val apkUri = FileProvider.getUriForFile(context, AndroidUtils.appFileProvider, apkFile)
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
      StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
      AppModuleAndroidUtils.openIntent(intent)
      StrictMode.setVmPolicy(vmPolicy)

      onUpdateClicked()
    } catch (error: Throwable) {
      if (AppModuleAndroidUtils.isDevBuild || AppModuleAndroidUtils.isBetaBuild) {
        throw error
      }

      Logger.e(TAG, "installApk(${apkFile.absolutePath}) error", error)

      dialogFactory.showDialog(
        context = context,
        params = KurobaComposeDialogController.informationDialog(
          title = KurobaComposeDialogController.Text.String(
            appResources.string(R.string.update_failed_to_install)
          ),
          description = KurobaComposeDialogController.Text.String(
            appResources.string(
              R.string.update_failed_to_install_description,
              error.errorMessageOrClassName()
            )
          )
        )
      )
    }
  }

  private enum class RequestPermissionResult {
    PermissionGranted,
    RetryPermissionRequest,
    Canceled
  }

  private suspend fun updateInstallRequested(
    responseRelease: UpdateApiRequest.ApkReleaseInfo,
    onUpdateClicked: () -> Unit
  ) {
    if (AndroidUtils.isAndroidT) {
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
          appResources.string(R.string.update_storage_permission_required_title),
          appResources.string(R.string.update_storage_permission_required),
          object : RuntimePermissionsHelper.PermissionRequiredDialogCallback {
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

  private fun getAndResetApkUpdateInfo(): ApkUpdateInfoJson? {
    val apkUpdateInfoJson = kurobaSettings.internal.apkUpdateInfoJson.readBlocking()
    kurobaSettings.internal.apkUpdateInfoJson.resetBlocking()

    val versionCode = apkUpdateInfoJson.versionCode
    val buildNumber = apkUpdateInfoJson.buildNumber
    val versionName = apkUpdateInfoJson.versionName

    if (versionCode <= 0L) {
      return null
    }

    return ApkUpdateInfoJson(versionCode, buildNumber, versionName)
  }

  private suspend fun canContinueBetaUpdate(versionCode: UpdateApiRequest.VersionCode.Beta): Boolean {
    val previousBuildNumber = kurobaSettings.internal.previousBuildNumber.read()

    Logger.debug(TAG) {
      "canContinueBetaUpdate() " +
      "responseRelease.versionCode: ${versionCode}, " +
      "BuildConfig.VERSION_CODE: ${BuildConfig.VERSION_CODE}, " +
      "previousBuildNumber: ${previousBuildNumber}"
    }

    if (versionCode.code < BuildConfig.VERSION_CODE.toLong()) {
      Logger.debug(TAG) { "canContinueBetaUpdate() responseRelease.versionCode < BuildConfig.VERSION_CODE.toLong()" }
      // Do not update if release's version code is less than ours
      return false
    }

    if (versionCode.code > BuildConfig.VERSION_CODE) {
      Logger.debug(TAG) { "canContinueBetaUpdate() responseRelease.versionCode > BuildConfig.VERSION_CODE.toLong()" }
      // Always update if the release's version code is greater than ours
      return true
    }

    // If they are the same then check the build numbers
    val buildNumberIsGreater = versionCode.buildNumber > previousBuildNumber
    Logger.debug(TAG) {
      "canContinueBetaUpdate() " +
        "buildNumber: ${versionCode.buildNumber} > previousBuilderNumber: ${previousBuildNumber}, " +
        "buildNumberIsGreater: ${buildNumberIsGreater}"
    }

    return buildNumberIsGreater
  }

  private fun canContinueReleaseUpdate(versionCode: UpdateApiRequest.VersionCode.Release): Boolean {
    Logger.debug(TAG) {
      "canContinueReleaseUpdate() versionCode: ${versionCode}, " +
      "BuildConfig.VERSION_CODE: ${BuildConfig.VERSION_CODE}"
    }

    return versionCode.code > BuildConfig.VERSION_CODE
  }

  companion object {
    private const val TAG = "UpdateManager"
  }
}