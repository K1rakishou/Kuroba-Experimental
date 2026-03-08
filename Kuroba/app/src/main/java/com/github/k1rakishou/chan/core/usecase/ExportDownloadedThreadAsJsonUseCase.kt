package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import android.net.Uri
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadingDelegate
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportDownloadedThreadAsJsonUseCase(
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val gson: Gson,
  private val fileManager: FileManager,
  private val chanPostRepository: ChanPostRepository
) : ISuspendUseCase<ExportDownloadedThreadAsJsonUseCase.Params, ModularResult<Unit>> {

  override suspend fun execute(parameter: Params): ModularResult<Unit> {
    return ModularResult.Try {
      val outputDirUri = parameter.outputDirUri
      val threadDescriptors = parameter.threadDescriptors
      val onUpdate = parameter.onUpdate

      withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onUpdate(0, threadDescriptors.size) }

        threadDescriptors.forEachIndexed { index, threadDescriptor ->
          ensureActive()

          val outputDir = fileManager.fromUri(outputDirUri)
            ?: throw ThreadExportException("Failed to get output file for directory: \'$outputDirUri\'")

          val fileName = "${threadDescriptor.siteName()}_${threadDescriptor.boardCode()}_${threadDescriptor.threadNo}_json.zip"
          val outputFile = fileManager.createFile(outputDir, fileName)
            ?: throw ThreadExportException("Failed to create output file \'$fileName\' in directory \'${outputDir}\'")

          try {
            exportThreadAsJson(outputFile, threadDescriptor)
          } catch (error: Throwable) {
            fileManager.fromUri(outputDirUri)?.let { file ->
              if (fileManager.isFile(file)) {
                fileManager.delete(file)
              }
            }

            throw error
          }

          withContext(Dispatchers.Main) { onUpdate(index + 1, threadDescriptors.size) }
        }

        withContext(Dispatchers.Main) { onUpdate(threadDescriptors.size, threadDescriptors.size) }
      }
    }
  }

  private suspend fun exportThreadAsJson(
    outputFile: AbstractFile,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    val postsLoadResult = chanPostRepository.getThreadPostsFromDatabase(threadDescriptor)

    val chanPosts = if (postsLoadResult is ModularResult.Error) {
      throw postsLoadResult.error
    } else {
      postsLoadResult as ModularResult.Value
      postsLoadResult.value.sortedBy { post -> post.postNo() }
    }

    if (chanPosts.isEmpty()) {
      throw ThreadExportException("Failed to load posts to export")
    }

    if (chanPosts.first() !is ChanOriginalPost) {
      throw ThreadExportException("First post is not OP")
    }

    val outputFileUri = outputFile.getFullPath()
    Logger.d(TAG, "exportThreadAsJson exporting ${chanPosts.size} posts into file '$outputFileUri'")

    val outputStream = fileManager.getOutputStream(outputFile)
    if (outputStream == null) {
      throw ThreadExportException("Failed to open output stream for file '${outputFileUri}'")
    }

    runInterruptible {
      outputStream.use { os ->
        ZipOutputStream(os).use { zos ->
          zos.putNextEntry(ZipEntry("thread_data.json"))

          ByteArrayInputStream(gson.toJson(chanPosts).toByteArray())
            .use { postsJsonByteArray -> postsJsonByteArray.copyTo(zos) }

          val threadMediaDirName = ThreadDownloadingDelegate.formatDirectoryName(threadDescriptor)
          val threadMediaDir = File(appConstants.threadDownloaderCacheDir, threadMediaDirName)
          threadMediaDir.listFiles()?.forEach { mediaFile ->
            // Use this to skip exporting thumbnails
            val matcher = MEDIA_EXCLUDE_PATTERN.matcher(mediaFile.name)
            if (matcher.matches()) return@forEach

            zos.putNextEntry(ZipEntry(mediaFile.name))
            mediaFile.inputStream().use { mediaFileSteam ->
              mediaFileSteam.copyTo(zos)
            }
          }
        }
      }
    }

    Logger.d(TAG, "exportThreadAsJson done")
  }

  class ThreadExportException(message: String) : Exception(message)

  data class Params(
    val outputDirUri: Uri,
    val threadDescriptors: List<ChanDescriptor.ThreadDescriptor>,
    val onUpdate: (Int, Int) -> Unit
  )

  companion object {
    private const val TAG = "ExportDownloadedThreadAsJsonUseCase"
    private val MEDIA_EXCLUDE_PATTERN = Pattern.compile(".*s\\..+")
  }
}
