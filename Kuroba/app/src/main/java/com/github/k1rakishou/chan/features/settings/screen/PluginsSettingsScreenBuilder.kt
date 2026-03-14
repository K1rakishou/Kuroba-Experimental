package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromGithubUseCase
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromLocalDirectoryUseCase
import com.github.k1rakishou.chan.features.mpv.EditMpvConfController
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.directory.TemporaryDirectoryCallback
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException

class PluginsSettingsScreenBuilder(
  private val coroutineScope: CoroutineScope,
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val appRestarter: AppRestarter,
  private val appConstants: AppConstants,
  private val dialogFactory: DialogFactory,
  private val fileChooser: FileChooser,
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val installMpvNativeLibrariesFromGithubUseCase: InstallMpvNativeLibrariesFromGithubUseCase,
  private val installMpvNativeLibrariesFromLocalDirectoryUseCase: InstallMpvNativeLibrariesFromLocalDirectoryUseCase
) : SettingsScreenBuilder {

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildMpvPluginSettingGroup(context, settingActions)
    }
  }

  private suspend fun SettingsScreen.buildMpvPluginSettingGroup(
    context: Context,
    settingActions: SettingActions
  ) {
    addGroup(
      key = "mpv",
      title = appResources.string(R.string.settings_plugins_mpv_group)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_plugins_use_mpv) },
          description = { appResources.string(R.string.settings_plugins_use_mpv_description) },
          setting = kurobaSettings.application.useMpvVideoPlayer
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_plugins_use_config_file) },
          setting = kurobaSettings.application.mpvUseConfigFile,
          dependencies = listOf(kurobaSettings.application.useMpvVideoPlayer)
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "EditConfigFile",
          title = { appResources.string(R.string.settings_plugins_edit_config_file) },
          dependencies = listOf(kurobaSettings.application.mpvUseConfigFile),
          callback = {
            val editMpvConfController = EditMpvConfController(context)
            settingActions.presentController(editMpvConfController)
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "CheckMpvLibsState",
          title = { appResources.string(R.string.settings_plugins_libs_status) },
          description = { loadLibrariesAndShowStatus(context) },
          dependencies = listOf(kurobaSettings.application.useMpvVideoPlayer),
          callback = { showOptions(context, settingActions) }
        )
      )
    }
  }

  private suspend fun showOptions(context: Context, settingActions: SettingActions) {
    val items = mutableListOf<FloatingListMenuItem>()

    items += FloatingListMenuItem(
      ACTION_DELETE_INSTALLED_LIBS,
      appResources.string(R.string.settings_plugins_libs_delete_old_mpv_libs)
    )
    items += FloatingListMenuItem(
      ACTION_INSTALL_FROM_GITHUB,
      appResources.string(R.string.settings_plugins_libs_install_libs_from_github)
    )
    items += FloatingListMenuItem(
      ACTION_INSTALL_FROM_LOCAL_DIRECTORY,
      appResources.string(R.string.settings_plugins_libs_install_libs_from_local_directory)
    )

    val clickedItemId = suspendCancellableCoroutine<Int?> { continuation ->
      val floatingListMenuController = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = items,
        menuDismissListener = { continuation.resumeValueSafe(null) },
        itemClickListener = { clickedItem -> continuation.resumeValueSafe(clickedItem.key as Int) }
      )

      coroutineScope.launch { settingActions.presentController(floatingListMenuController) }

      continuation.invokeOnCancellation { cause ->
        if (cause != null) {
          floatingListMenuController.stopPresenting()
        }
      }
    }

    when (clickedItemId) {
      ACTION_INSTALL_FROM_GITHUB -> {
        installMpvLibrariesFromGithub(context, settingActions)
      }
      ACTION_INSTALL_FROM_LOCAL_DIRECTORY -> {
        installMpvLibrariesFromLocalDirectory(context, settingActions)
      }
      ACTION_DELETE_INSTALLED_LIBS -> {
        deleteOldMpvLibs(context)
      }
      null -> {
        // no-op
      }
    }
  }

  private fun deleteOldMpvLibs(context: Context) {
    appConstants.mpvNativeLibsDir.listFiles()?.forEach { libFile ->
      Logger.debug(TAG) { "Deleting lib file: \'${libFile.absolutePath}\'" }
      libFile.delete()
    }

    dialogFactory.showDialog(
      checkAppVisibility = false,
      context = context,
      params = KurobaComposeDialogController.Params(
        title = KurobaComposeDialogController.Text.Id(R.string.settings_plugins_libs_installation_success),
        description = KurobaComposeDialogController.Text.Id(R.string.settings_plugins_libs_old_libs_deleted),
        positiveButton = KurobaComposeDialogController.okButton()
      ),
      onDismissListener = { appRestarter.restart() }
    )
  }

  private suspend fun installMpvLibrariesFromLocalDirectory(
    context: Context,
    settingActions: SettingActions
  ) {
    suspendCancellableCoroutine<Unit> { continuation ->
      val supportedAbis = Build.SUPPORTED_ABIS.joinToString()

      dialogFactory.showDialog(
        checkAppVisibility = false,
        context = context,
        params = KurobaComposeDialogController.Params(
          title = KurobaComposeDialogController.Text.Id(R.string.settings_plugins_libs_local_installation_dialog_title),
          description = KurobaComposeDialogController.Text.String(
            appResources.string(
              R.string.settings_plugins_libs_local_installation_dialog_description,
              appConstants.mpvNativeLibsDir.absolutePath,
              supportedAbis
            )
          ),
          positiveButton = KurobaComposeDialogController.okButton()
        ),
        onDismissListener = { continuation.resumeValueSafe(Unit) }
      )
    }

    val uri = suspendCancellableCoroutine<Uri?> { cancellableContinuation ->
      fileChooser.openChooseDirectoryDialog(object : TemporaryDirectoryCallback() {
        override fun onCancel(reason: String) {
          cancellableContinuation.resumeValueSafe(null)
        }

        override fun onResult(uri: Uri) {
          cancellableContinuation.resumeValueSafe(uri)
        }
      })
    }

    if (uri == null) {
      settingActions.showToast(appResources.string(R.string.canceled))
      return
    }

    when (val result = installMpvNativeLibrariesFromLocalDirectoryUseCase.execute(uri)) {
      is ModularResult.Error -> {
        Logger.e(TAG, "installMpvLibrariesFromLocalDirectory error", result.error)

        dialogFactory.showDialog(
          checkAppVisibility = false,
          context = context,
          params = KurobaComposeDialogController.Params(
            title = KurobaComposeDialogController.Text.Id(R.string.settings_plugins_libs_installation_failure),
            description = KurobaComposeDialogController.Text.String(
              appResources.string(
                R.string.settings_plugins_libs_installation_description_failure,
                result.error.errorMessageOrClassName()
              )
            ),
            positiveButton = KurobaComposeDialogController.okButton()
          ),
        )
      }
      is ModularResult.Value -> {
        Logger.d(TAG, "installMpvLibrariesFromLocalDirectory success")

        copyMpvCaCert(context, appConstants)

        dialogFactory.showDialog(
          checkAppVisibility = false,
          context = context,
          params = KurobaComposeDialogController.Params(
            title = KurobaComposeDialogController.Text.Id(R.string.settings_plugins_libs_installation_success),
            description = KurobaComposeDialogController.Text.Id(
              R.string.settings_plugins_libs_installation_description_success
            ),
            positiveButton = KurobaComposeDialogController.okButton()
          ),
          onDismissListener = { appRestarter.restart() }
        )
      }
    }
  }

  private suspend fun installMpvLibrariesFromGithub(
    context: Context,
    settingActions: SettingActions
  ) {
    if (AppModuleAndroidUtils.flavorType == AndroidUtils.FlavorType.Fdroid) {
      settingActions.showToast(appResources.string(R.string.settings_plugins_libs_fdroid_github_error))
      return
    }

    val loadingViewController = LoadingViewController(
      context = context,
      indeterminate = true,
      title = appResources.string(R.string.settings_plugins_libs_downloading_libraries)
    )

    settingActions.presentController(loadingViewController)

    val result = try {
      installMpvNativeLibrariesFromGithubUseCase.execute(Unit)
    } finally {
      loadingViewController.stopPresenting()
    }

    when (result) {
      is ModularResult.Error -> {
        Logger.e(TAG, "installMpvLibrariesFromGithub error", result.error)

        dialogFactory.showDialog(
          checkAppVisibility = false,
          context = context,
          params = KurobaComposeDialogController.Params(
            title = KurobaComposeDialogController.Text.Id(R.string.settings_plugins_libs_installation_failure),
            description = KurobaComposeDialogController.Text.String(
              appResources.string(
                R.string.settings_plugins_libs_installation_description_failure,
                result.error.errorMessageOrClassName()
              )
            ),
            positiveButton = KurobaComposeDialogController.okButton()
          ),
        )
      }
      is ModularResult.Value -> {
        Logger.d(TAG, "installMpvLibrariesFromGithub success")

        copyMpvCaCert(context, appConstants)

        dialogFactory.showDialog(
          checkAppVisibility = false,
          context = context,
          params = KurobaComposeDialogController.Params(
            title = KurobaComposeDialogController.Text.Id(R.string.settings_plugins_libs_installation_success),
            description = KurobaComposeDialogController.Text.Id(
              R.string.settings_plugins_libs_installation_description_success
            ),
            positiveButton = KurobaComposeDialogController.okButton()
          ),
          onDismissListener = { appRestarter.restart() }
        )
      }
    }
  }

  private fun copyMpvCaCert(applicationContext: Context, appConstants: AppConstants) {
    Logger.d(TAG, "copyMpvCaCert() start")

    val assetManager = applicationContext.assets

    try {
      assetManager.open(AppConstants.MPV_CERTIFICATE_FILE_NAME, AssetManager.ACCESS_STREAMING).use { inputStream ->
        val mpvCertFile = File(appConstants.mpvCertDir, AppConstants.MPV_CERTIFICATE_FILE_NAME)

        if (mpvCertFile.exists()) {
          val deleteSuccess = mpvCertFile.delete()
          Logger.d(TAG, "Deleting old cert file: ${mpvCertFile.absolutePath}, success: $deleteSuccess")
        }

        val createSuccess = mpvCertFile.createNewFile()
        Logger.d(TAG, "Creating new cert file: ${mpvCertFile.absolutePath}, success: $createSuccess")

        mpvCertFile.outputStream().use { outputStream ->
          inputStream.copyTo(outputStream)
        }

        Logger.d(TAG, "Copied asset file: ${AppConstants.MPV_CERTIFICATE_FILE_NAME}")
      }
    } catch (e: IOException) {
      Logger.e(TAG, "Failed to copy asset file: ${AppConstants.MPV_CERTIFICATE_FILE_NAME}", e)
    }

    Logger.d(TAG, "copyMpvCaCert() end")
  }

  private fun loadLibrariesAndShowStatus(context: Context): String {
    return buildString {
      append(getOverallStatus(context))
      appendLine()
      appendLine()
      append(getLibsStatus(context))
    }
  }

  private fun getLibsStatus(context: Context): String {
    return MPVLib.getInstalledLibraries(context, appConstants.mpvNativeLibsDir)
      .entries
      .joinToString(
        separator = "\n",
        transform = { entry ->
          val libName = entry.key
          val installed = entry.value

          val res = if (installed) {
            appResources.string(R.string.settings_plugins_libs_status_lib_installed)
          } else {
            appResources.string(R.string.settings_plugins_libs_status_lib_missing)
          }

          return@joinToString "${libName}: ${res}"
        })
  }

  private fun getOverallStatus(context: Context): String {
    if (!MPVLib.checkLibrariesInstalled(context, appConstants.mpvNativeLibsDir)) {
      return appResources.string(R.string.settings_plugins_libs_status_no_libs)
    }

    MPVLib.tryLoadLibraries(appConstants.mpvNativeLibsDir)

    val lastError = MPVLib.getLastError()
    if (lastError != null) {
      return appResources.string(R.string.settings_plugins_libs_status_load_error, lastError.errorMessageOrClassName())
    }

    val playerVersion = try {
      MPVLib.mpvPlayerVersion() ?: -1
    } catch (ignored: Throwable) {
      -1
    }

    if (playerVersion != MPVLib.SUPPORTED_MPV_PLAYER_VERSION) {
      return appResources.string(
        R.string.settings_plugins_libs_status_player_version_app_version_differ,
        playerVersion,
        MPVLib.SUPPORTED_MPV_PLAYER_VERSION
      )
    }

    return appResources.string(R.string.settings_plugins_libs_status_ok, playerVersion)
  }

  companion object {
    private const val TAG = "PluginSettingsScreen"

    private const val ACTION_INSTALL_FROM_GITHUB = 0
    private const val ACTION_INSTALL_FROM_LOCAL_DIRECTORY = 1
    private const val ACTION_DELETE_INSTALLED_LIBS = 2
  }

}