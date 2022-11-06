package com.github.k1rakishou.chan.core.usecase

import android.net.Uri
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class ExportDownloadedThreadMediaUseCase(
  private val appConstants: AppConstants,
  private val fileManager: FileManager
) : ISuspendUseCase<ExportDownloadedThreadMediaUseCase.Params, ModularResult<Unit>> {

  override suspend fun execute(parameter: Params): ModularResult<Unit> {
    return ModularResult.Try {
      val outputDirUri = parameter.outputDirectoryUri
      val threadDescriptors = parameter.threadDescriptors
      val onUpdate = parameter.onUpdate

      withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onUpdate(0, threadDescriptors.size) }

        threadDescriptors.forEachIndexed { index, threadDescriptor ->
          ensureActive()

          val outputDir = fileManager.fromUri(outputDirUri)
            ?: throw MediaExportException("Failed to get output file for directory: \'$outputDirUri\'")

          val directoryName = "${threadDescriptor.siteName()}_${threadDescriptor.boardCode()}_${threadDescriptor.threadNo}"

          val outputDirectory = fileManager.createDir(outputDir, directoryName)
            ?: throw MediaExportException("Failed to create output directory \'$directoryName\' in directory \'${outputDir}\'")

          exportThreadMedia(
            coroutineScope = this,
            outputDirectory = outputDirectory,
            threadDescriptor = threadDescriptor
          )

          withContext(Dispatchers.Main) { onUpdate(index + 1, threadDescriptors.size) }
        }

        withContext(Dispatchers.Main) { onUpdate(threadDescriptors.size, threadDescriptors.size) }
      }
    }
  }

  private fun exportThreadMedia(
    coroutineScope: CoroutineScope,
    outputDirectory: AbstractFile,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    val threadMediaDirName = ThreadDownloadingDelegate.formatDirectoryName(threadDescriptor)
    val threadMediaDir = File(appConstants.threadDownloaderCacheDir, threadMediaDirName)

    val threadMediaDirFiles = threadMediaDir.listFiles()
      ?: emptyArray()

    Logger.d(TAG, "exportThreadMedia() start, totalFilesCount=${threadMediaDirFiles.size}")

    var succeeded = 0

    threadMediaDirFiles.forEach { mediaFile ->
      coroutineScope.ensureActive()

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

  class MediaExportException(message: String) : Exception(message)

  data class Params(
    val outputDirectoryUri: Uri,
    val threadDescriptors: List<ChanDescriptor.ThreadDescriptor>,
    val onUpdate: (Int, Int) -> Unit
  )

  companion object {
    private const val TAG = "ExportDownloadedThreadMediaUseCase"
  }
}