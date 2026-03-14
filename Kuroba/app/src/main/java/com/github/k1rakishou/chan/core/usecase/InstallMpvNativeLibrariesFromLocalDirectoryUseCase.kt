package com.github.k1rakishou.chan.core.usecase

import android.net.Uri
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
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

    val files = fileManager.listFiles(directory)
      .filter { file -> fileManager.getName(file).endsWith(".so") }

    if (files.isEmpty()) {
      throw MpvInstallLibsFromDirectoryException("No \'.so\' files found in the directory \'$uri\'")
    }

    appConstants.mpvNativeLibsDir.listFiles()?.forEach { libFile ->
      Logger.d(TAG, "Deleting old mpv library file: \'${libFile.absolutePath}\'")
      libFile.delete()
    }

    files.forEach { file ->
      val fileName = fileManager.getName(file)
      if (fileName == AppConstants.MPV_CERTIFICATE_FILE_NAME) {
        Logger.d(TAG, "Moving cacert.pem")
        copyCertificateFile(file)
        return@forEach
      }

      val isLibraryExpected = MPVLib.LIBS.any { expectedLibName ->
        if (expectedLibName.equals(fileName, ignoreCase = true)) {
          return@any true
        }

        return@any false
      }

      if (!isLibraryExpected) {
        Logger.d(TAG, "Skipping \'${fileName}\'")
        return@forEach
      }

      val outputFile = File(appConstants.mpvNativeLibsDir, fileName)

      Logger.d(TAG, "Moving mpv library file: \'${file.getFullPath()}\' into \'${outputFile.absolutePath}\'")

      val inputStream = fileManager.getInputStream(file)
        ?: throw MpvInstallLibsFromDirectoryException("Failed to get input stream for file \'${file.getFullPath()}\'")

      inputStream.use { input ->
        outputFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }

      Logger.d(TAG, "Done")
    }

    Logger.d(TAG, "All done")
  }

  private fun copyCertificateFile(libFile: AbstractFile) {
    val mpvCertFile = File(appConstants.mpvCertDir, AppConstants.MPV_CERTIFICATE_FILE_NAME)
    if (mpvCertFile.exists()) {
      val deleteSuccess = mpvCertFile.delete()
      Logger.d(TAG, "Deleting old cert file: ${mpvCertFile.absolutePath}, success: $deleteSuccess")
    }

    val createSuccess = mpvCertFile.createNewFile()
    Logger.d(TAG, "Creating new cert file: ${mpvCertFile.absolutePath}, success: $createSuccess")

    val inputStream = fileManager.getInputStream(libFile)
      ?: throw MpvInstallLibsFromDirectoryException("Failed to get input stream for file \'${libFile.getFullPath()}\'")

    inputStream.use { input ->
      mpvCertFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }

    Logger.d(TAG, "Copied ${AppConstants.MPV_CERTIFICATE_FILE_NAME}")
  }

  class MpvInstallLibsFromDirectoryException(message: String) : Exception(message)

  companion object {
    private const val TAG = "InstallMpvNativeLibrariesFromLocalDirectoryUseCase"
  }

}