package com.github.k1rakishou.chan.core.usecase

import android.net.Uri
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ExportDownloadedThreadMediaUseCase(
  private val appConstants: AppConstants,
  private val fileManager: FileManager
) : ISuspendUseCase<ExportDownloadedThreadMediaUseCase.Params, ModularResult<Unit>> {

  override suspend fun execute(parameter: Params): ModularResult<Unit> {
    return ModularResult.Try {
      val outputFileUri = parameter.outputDirectoryUri
      val directoryName = parameter.directoryName
      val threadDescriptor = parameter.threadDescriptor

      withContext(Dispatchers.IO) {
        exportThreadMedia(
          outputDirectoryUri = outputFileUri,
          directoryName = directoryName,
          threadDescriptor = threadDescriptor
        )
      }
    }
  }

  private fun exportThreadMedia(
    outputDirectoryUri: Uri,
    directoryName: String,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    val directory = fileManager.fromUri(outputDirectoryUri)
      ?: throw IOException("fileManager.fromUri(${outputDirectoryUri}) -> null")

    val outputDirectory = fileManager.createDir(directory, directoryName)
      ?: throw IOException("fileManager.createDir() -> null")

    val threadMediaDirName = ThreadDownloadingDelegate.formatDirectoryName(threadDescriptor)
    val threadMediaDir = File(appConstants.threadDownloaderCacheDir, threadMediaDirName)

    val threadMediaDirFiles = threadMediaDir.listFiles()
      ?: emptyArray()

    Logger.d(TAG, "exportThreadMedia() start, totalFilesCount=${threadMediaDirFiles.size}")

    var succeeded = 0

    threadMediaDirFiles.forEach { mediaFile ->
      val outputFile = fileManager.createFile(outputDirectory, mediaFile.name)
      if (outputFile == null) {
        Logger.e(TAG, "fileManager.createFile(${outputDirectory}, ${mediaFile.name}) -> null")
        return@forEach
      }

      val success = fileManager.copyFileContents(
        sourceFile = fileManager.fromRawFile(mediaFile),
        destinationFile = outputFile
      )

      if (success) {
        ++succeeded
      }
    }

    Logger.d(TAG, "exportThreadMedia() end, succeeded=${succeeded}, totalFilesCount=${threadMediaDirFiles.size}")
  }

  data class Params(
    val outputDirectoryUri: Uri,
    val directoryName: String,
    val threadDescriptor: ChanDescriptor.ThreadDescriptor
  )

  companion object {
    private const val TAG = "ExportDownloadedThreadMediaUseCase"
  }
}