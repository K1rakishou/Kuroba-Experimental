package com.github.k1rakishou.chan.core.usecase

import android.net.Uri
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import java.io.File

class InstallMpvNativeLibrariesFromLocalDirectoryUseCase(
  private val appConstants: AppConstants,
  private val fileManager: FileManager
) : ISuspendUseCase<Uri, ModularResult<Unit>> {

  override suspend fun execute(parameter: Uri): ModularResult<Unit> {
    return ModularResult.Try { executeInternal(parameter) }
  }

  private fun executeInternal(uri: Uri) {
    val directory = fileManager.fromUri(uri)
      ?: throw MpvInstallLibsFromDirectoryException("Failed to open directory uri: \'$uri\'")

    val libFiles = fileManager.listFiles(directory)
      .filter { file -> fileManager.getName(file).endsWith(".so") }

    if (libFiles.isEmpty()) {
      throw MpvInstallLibsFromDirectoryException("No \'.so\' files found in the directory \'$uri\'")
    }

    appConstants.mpvNativeLibsDir.listFiles()?.forEach { libFile ->
      Logger.d(TAG, "Deleting old mpv library file: \'${libFile.absolutePath}\'")
      libFile.delete()
    }

    libFiles.forEach { libFile ->
      val libName = fileManager.getName(libFile)

      val isLibraryExpected = MPVLib.LIBS.any { expectedLibName ->
        if (expectedLibName.equals(libName, ignoreCase = true)) {
          return@any true
        }

        return@any false
      }

      if (!isLibraryExpected) {
        Logger.d(TAG, "Skipping \'${libName}\'")
        return@forEach
      }

      val outputFile = File(appConstants.mpvNativeLibsDir, libName)

      Logger.d(TAG, "Moving mpv library file: \'${libFile.getFullPath()}\' into \'${outputFile.absolutePath}\'")

      val inputStream = fileManager.getInputStream(libFile)
        ?: throw MpvInstallLibsFromDirectoryException("Failed to get input stream for file \'${libFile.getFullPath()}\'")

      inputStream.use { input ->
        outputFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }

      Logger.d(TAG, "Done")
    }

    Logger.d(TAG, "All done")
  }

  class MpvInstallLibsFromDirectoryException(message: String) : Exception(message)

  companion object {
    private const val TAG = "InstallMpvNativeLibrariesFromLocalDirectoryUseCase"
  }

}