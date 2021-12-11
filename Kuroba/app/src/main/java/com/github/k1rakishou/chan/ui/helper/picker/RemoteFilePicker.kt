package com.github.k1rakishou.chan.ui.helper.picker

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException

class RemoteFilePicker(
  appConstants: AppConstants,
  fileManager: FileManager,
  replyManager: ReplyManager,
  private val appScope: CoroutineScope,
  private val fileCacheV2: Lazy<FileCacheV2>,
  private val cacheHandler: Lazy<CacheHandler>
) : AbstractFilePicker<RemoteFilePicker.RemoteFilePickerInput>(appConstants, replyManager, fileManager) {
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)
  private val cacheFileType = CacheFileType.Other

  override suspend fun pickFile(filePickerInput: RemoteFilePickerInput): ModularResult<PickedFile> {
    if (filePickerInput.imageUrl.isEmpty()) {
      return ModularResult.error(FilePickerError.BadUrl(filePickerInput.imageUrl))
    }

    val imageUrl = filePickerInput.imageUrl.toHttpUrlOrNull()
    if (imageUrl == null) {
      return ModularResult.error(FilePickerError.BadUrl(filePickerInput.imageUrl))
    }

    return withContext(Dispatchers.IO) {
      val downloadedFileMaybe = downloadFile(
        imageUrl = imageUrl,
        showLoadingView = filePickerInput.showLoadingView,
        hideLoadingView = filePickerInput.hideLoadingView
      )

      if (downloadedFileMaybe is ModularResult.Error) {
        val error = FilePickerError.FailedToDownloadFile(
          filePickerInput.imageUrl,
          downloadedFileMaybe.error
        )

        return@withContext ModularResult.error(error)
      }

      val downloadedFile = (downloadedFileMaybe as ModularResult.Value).value
      val fileName = getRemoteFileName(imageUrl)

      val copyResult = copyDownloadedFileToReplyFileStorage(
        downloadedFile,
        fileName,
        filePickerInput.replyChanDescriptor
      )

      if (copyResult is ModularResult.Error) {
        return@withContext ModularResult.error(FilePickerError.UnknownError(copyResult.error))
      }

      return@withContext copyResult
    }
  }

  private fun copyDownloadedFileToReplyFileStorage(
    downloadedFile: File,
    originalFileName: String,
    replyChanDescriptor: ChanDescriptor
  ): ModularResult<PickedFile> {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try {
      val reply = replyManager.getReplyOrNull(replyChanDescriptor)
      if (reply == null) {
        return@Try PickedFile.Failure(FilePickerError.NoReplyFound(replyChanDescriptor))
      }

      val uniqueFileName = replyManager.generateUniqueFileName(appConstants)

      val replyFile = replyManager.createNewEmptyAttachFile(
        uniqueFileName,
        originalFileName,
        System.currentTimeMillis()
      )

      if (replyFile == null) {
        return@Try PickedFile.Failure(FilePickerError.FailedToGetAttachFile())
      }

      val fileUuid = replyFile.getReplyFileMeta().valueOrNull()?.fileUuid
      if (fileUuid == null) {
        return@Try PickedFile.Failure(FilePickerError.FailedToCreateFileMeta())
      }

      copyDownloadedFileIntoReplyFile(downloadedFile, replyFile)
      cacheHandler.get().deleteCacheFile(
        cacheFileType = cacheFileType,
        cacheFile = downloadedFile
      )

      return@Try PickedFile.Result(listOf(replyFile))
    }
  }

  private fun copyDownloadedFileIntoReplyFile(
    downloadedFile: File,
    replyFile: ReplyFile
  ) {
    val outputFile = replyFile.fileOnDisk

    downloadedFile.inputStream().use { inputStream ->
      outputFile.outputStream().use { outputStream ->
        if (!IOUtils.copy(inputStream, outputStream, MAX_FILE_SIZE)) {
          throw IOException(
            "Failed to copy downloaded file (downloadedFile='${downloadedFile.absolutePath}') " +
              "into reply file (filePath='${outputFile.absolutePath}')"
          )
        }
      }
    }
  }

  private suspend fun downloadFile(
    imageUrl: HttpUrl,
    showLoadingView: suspend (Int) -> Unit,
    hideLoadingView: suspend () -> Unit
  ): ModularResult<File> {
    val urlString = imageUrl.toString()

    serializedCoroutineExecutor.post {
      showLoadingView.invoke(R.string.downloading_file)
    }

    return suspendCancellableCoroutine { cancellableContinuation ->
      val cancelableDownload = fileCacheV2.get().enqueueDownloadFileRequest(
        url = urlString,
        cacheFileType = cacheFileType,
        callback = object : FileCacheListener() {
          override fun onSuccess(file: File) {
            super.onSuccess(file)

            cancellableContinuation.resumeValueSafe(ModularResult.value(file))
          }

          override fun onNotFound() {
            super.onNotFound()

            onError(FilePickerError.FileNotFound(urlString))
          }

          override fun onFail(exception: Exception) {
            super.onFail(exception)

            onError(FilePickerError.UnknownError(exception))
          }

          override fun onCancel() {
            super.onCancel()

            onError(FilePickerError.Canceled())
          }

          override fun onEnd() {
            super.onEnd()

            serializedCoroutineExecutor.post {
              hideLoadingView.invoke()
            }
          }

          private fun onError(error: FilePickerError) {
            cancellableContinuation.resumeValueSafe(ModularResult.error(error))
          }
        })

      cancellableContinuation.invokeOnCancellation { cause ->
        if (cause == null) {
          return@invokeOnCancellation
        }

        cancelableDownload.cancel()
      }
    }
  }

  data class RemoteFilePickerInput(
    val notifyListeners: Boolean,
    val replyChanDescriptor: ChanDescriptor,
    val imageUrl: String,
    val showLoadingView: suspend (Int) -> Unit,
    val hideLoadingView: suspend () -> Unit
  )

}