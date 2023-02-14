package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromGithubUseCase
import com.github.k1rakishou.chan.core.usecase.InstallMpvNativeLibrariesFromLocalDirectoryUseCase
import com.github.k1rakishou.chan.features.mpv.EditMpvConfController
import com.github.k1rakishou.chan.features.settings.PluginsScreen
import com.github.k1rakishou.chan.features.settings.SettingClickAction
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getFlavorType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.directory.TemporaryDirectoryCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException

class PluginSettingsScreen(
  context: Context,
  private val appConstants: AppConstants,
  private val appRestarter: AppRestarter,
  private val dialogFactory: DialogFactory,
  private val fileChooser: FileChooser,
  private val globalWindowInsetsManager: GlobalWindowInsetsManager,
  private val navigationController: NavigationController,
  private val installMpvNativeLibrariesFromGithubUseCase: InstallMpvNativeLibrariesFromGithubUseCase,
  private val installMpvNativeLibrariesFromLocalDirectoryUseCase: InstallMpvNativeLibrariesFromLocalDirectoryUseCase
) : BaseSettingsScreen(
  context,
  PluginsScreen,
  R.string.settings_plugins
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMpvPluginSettingGroup()
    )
  }

  private fun buildMpvPluginSettingGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = PluginsScreen.MpvPluginGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_plugins_mpv_group),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = PluginsScreen.MpvPluginGroup.UseMpv,
          topDescriptionIdFunc = { R.string.settings_plugins_use_mpv },
          bottomDescriptionIdFunc = { R.string.settings_plugins_use_mpv_description },
          setting = ChanSettings.useMpvVideoPlayer
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = PluginsScreen.MpvPluginGroup.UseConfigFile,
          topDescriptionIdFunc = { R.string.settings_plugins_use_config_file },
          setting = ChanSettings.mpvUseConfigFile
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = PluginsScreen.MpvPluginGroup.EditConfigFile,
          topDescriptionIdFunc = { R.string.settings_plugins_edit_config_file },
          dependsOnSetting = ChanSettings.mpvUseConfigFile,
          callback = {
            val editMpvConfController = EditMpvConfController(context)
            navigationController.presentController(editMpvConfController)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = PluginsScreen.MpvPluginGroup.CheckMpvLibsState,
          topDescriptionIdFunc = { R.string.settings_plugins_libs_status },
          bottomDescriptionStringFunc = { loadLibrariesAndShowStatus() },
          callbackWithClickAction = {
            showOptions()
            SettingClickAction.NoAction
          },
          dependsOnSetting = ChanSettings.useMpvVideoPlayer
        )

        group
      }
    )
  }

  private suspend fun showOptions() {
    val items = mutableListOf<FloatingListMenuItem>()

    items += FloatingListMenuItem(
      ACTION_DELETE_INSTALLED_LIBS,
      getString(R.string.settings_plugins_libs_delete_old_mpv_libs)
    )
    items += FloatingListMenuItem(
      ACTION_INSTALL_FROM_GITHUB,
      getString(R.string.settings_plugins_libs_install_libs_from_github)
    )
    items += FloatingListMenuItem(
      ACTION_INSTALL_FROM_LOCAL_DIRECTORY,
      getString(R.string.settings_plugins_libs_install_libs_from_local_directory)
    )

    val clickedItemId = suspendCancellableCoroutine<Int?> { cancellableContinuation ->
      val floatingListMenuController = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = items,
        menuDismissListener = { cancellableContinuation.resumeValueSafe(null) },
        itemClickListener = { clickedItem -> cancellableContinuation.resumeValueSafe(clickedItem.key as Int) }
      )

      navigationController.presentController(floatingListMenuController)

      cancellableContinuation.invokeOnCancellation { cause ->
        if (cause != null) {
          floatingListMenuController.stopPresenting()
        }
      }
    }

    when (clickedItemId) {
      ACTION_INSTALL_FROM_GITHUB -> {
        installMpvLibrariesFromGithub()
      }
      ACTION_INSTALL_FROM_LOCAL_DIRECTORY -> {
        installMpvLibrariesFromLocalDirectory()
      }
      ACTION_DELETE_INSTALLED_LIBS -> {
        deleteOldMpvLibs()
      }
      null -> {
        // no-op
      }
    }
  }

  private fun deleteOldMpvLibs() {
    appConstants.mpvNativeLibsDir.listFiles()?.forEach { libFile ->
      Logger.d(TAG, "Deleting lib file: \'${libFile.absolutePath}\'")
      libFile.delete()
    }

    dialogFactory.createSimpleInformationDialog(
      checkAppVisibility = false,
      context = context,
      titleText = getString(R.string.settings_plugins_libs_installation_success),
      descriptionText = getString(R.string.settings_plugins_libs_old_libs_deleted),
      onDismissListener = { appRestarter.restart() }
    )
  }

  private suspend fun installMpvLibrariesFromLocalDirectory() {
    suspendCancellableCoroutine<Unit> { cancellableContinuation ->
      val supportedAbis = Build.SUPPORTED_ABIS.joinToString()

      dialogFactory.createSimpleInformationDialog(
        checkAppVisibility = false,
        context = context,
        titleText = getString(R.string.settings_plugins_libs_local_installation_dialog_title),
        descriptionText = getString(
          R.string.settings_plugins_libs_local_installation_dialog_description,
          appConstants.mpvNativeLibsDir.absolutePath,
          supportedAbis
        ),
        onDismissListener = { cancellableContinuation.resumeValueSafe(Unit) }
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
      showToast(context, R.string.canceled)
      return
    }

    when (val result = installMpvNativeLibrariesFromLocalDirectoryUseCase.execute(uri)) {
      is ModularResult.Error -> {
        Logger.e(TAG, "installMpvLibrariesFromLocalDirectory error", result.error)

        dialogFactory.createSimpleInformationDialog(
          checkAppVisibility = false,
          context = context,
          titleText = getString(R.string.settings_plugins_libs_installation_failure),
          descriptionText = getString(
            R.string.settings_plugins_libs_installation_description_failure,
            result.error.errorMessageOrClassName()
          )
        )
      }
      is ModularResult.Value -> {
        Logger.d(TAG, "installMpvLibrariesFromLocalDirectory success")

        copyMpvCaCert(context, appConstants)

        dialogFactory.createSimpleInformationDialog(
          checkAppVisibility = false,
          context = context,
          titleText = getString(R.string.settings_plugins_libs_installation_success),
          descriptionText = getString(R.string.settings_plugins_libs_installation_description_success),
          onDismissListener = { appRestarter.restart() }
        )
      }
    }
  }

  private suspend fun installMpvLibrariesFromGithub() {
    if (getFlavorType() == AndroidUtils.FlavorType.Fdroid) {
      showToast(context, getString(R.string.settings_plugins_libs_fdroid_github_error))
      return
    }

    val loadingViewController = LoadingViewController(
      context,
      true,
      getString(R.string.settings_plugins_libs_downloading_libraries)
    )

    navigationController.presentController(loadingViewController)

    val result = try {
      installMpvNativeLibrariesFromGithubUseCase.execute(Unit)
    } finally {
      loadingViewController.stopPresenting()
    }

    when (result) {
      is ModularResult.Error -> {
        Logger.e(TAG, "installMpvLibrariesFromGithub error", result.error)

        dialogFactory.createSimpleInformationDialog(
          checkAppVisibility = false,
          context = context,
          titleText = getString(R.string.settings_plugins_libs_installation_failure),
          descriptionText = getString(
            R.string.settings_plugins_libs_installation_description_failure,
            result.error.errorMessageOrClassName()
          )
        )
      }
      is ModularResult.Value -> {
        Logger.d(TAG, "installMpvLibrariesFromGithub success")

        copyMpvCaCert(context, appConstants)

        dialogFactory.createSimpleInformationDialog(
          checkAppVisibility = false,
          context = context,
          titleText = getString(R.string.settings_plugins_libs_installation_success),
          descriptionText = getString(R.string.settings_plugins_libs_installation_description_success),
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

  private fun loadLibrariesAndShowStatus(): String {
    return buildString {
      append(getOverallStatus())
      appendLine()
      appendLine()
      append(getLibsStatus())
    }
  }

  private fun getLibsStatus(): String {
    return MPVLib.getInstalledLibraries(context, appConstants.mpvNativeLibsDir)
      .entries
      .joinToString(
        separator = "\n",
        transform = { entry ->
          val libName = entry.key
          val installed = entry.value

          val res = if (installed) {
            getString(R.string.settings_plugins_libs_status_lib_installed)
          } else {
            getString(R.string.settings_plugins_libs_status_lib_missing)
          }

          return@joinToString "${libName}: ${res}"
        })
  }

  private fun getOverallStatus(): String {
    if (!MPVLib.checkLibrariesInstalled(context, appConstants.mpvNativeLibsDir)) {
      return getString(R.string.settings_plugins_libs_status_no_libs)
    }

    MPVLib.tryLoadLibraries(appConstants.mpvNativeLibsDir)

    val lastError = MPVLib.getLastError()
    if (lastError != null) {
      return getString(R.string.settings_plugins_libs_status_load_error, lastError.errorMessageOrClassName())
    }

    val playerVersion = try {
      MPVLib.mpvPlayerVersion()
    } catch (error: Throwable) {
      -1
    }

    if (playerVersion != MPVLib.SUPPORTED_MPV_PLAYER_VERSION) {
      return getString(
        R.string.settings_plugins_libs_status_player_version_app_version_differ,
        playerVersion,
        MPVLib.SUPPORTED_MPV_PLAYER_VERSION
      )
    }

    return getString(R.string.settings_plugins_libs_status_ok, playerVersion)
  }

  companion object {
    private const val TAG = "PluginSettingsScreen"

    private const val ACTION_INSTALL_FROM_GITHUB = 0
    private const val ACTION_INSTALL_FROM_LOCAL_DIRECTORY = 1
    private const val ACTION_DELETE_INSTALLED_LIBS = 2
  }

}