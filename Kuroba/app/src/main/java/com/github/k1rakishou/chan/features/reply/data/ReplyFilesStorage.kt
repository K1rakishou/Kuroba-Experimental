package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.ModularResult.Companion.value
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.Gson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.*

class ReplyFilesStorage(
  private val gson: Gson,
  private val appConstants: AppConstants,
  private val replyFiles: MutableList<ReplyFile> = mutableListOf()
) {
  private val replyFilesUpdates = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  fun listenForReplyFilesUpdates(): Flow<Unit> {
    return replyFilesUpdates.asSharedFlow()
  }

  private fun onReplyFilesChanged() {
    replyFilesUpdates.tryEmit(Unit)
  }

  @Synchronized
  fun takeSelectedFiles(chanDescriptor: ChanDescriptor, notifyListeners: Boolean = true): ModularResult<List<ReplyFile>> {
    return Try {
      val takenFiles = mutableListOf<ReplyFile>()

      for (replyFile in replyFiles) {
        val replyFileMeta = replyFile.getReplyFileMeta().unwrap()
        if (!replyFileMeta.selected) {
          continue
        }

        if (replyFileMeta.isTaken()) {
          continue
        }

        val takeFileResult = replyFile.markFileAsTaken(chanDescriptor)
        if (takeFileResult is ModularResult.Error) {
          Logger.e(TAG, "markFileAsTaken($chanDescriptor) error", takeFileResult.error)
          continue
        }

        val managedToTakeFile = (takeFileResult as ModularResult.Value).value
        if (!managedToTakeFile) {
          continue
        }

        takenFiles += replyFile
      }

      ensureFilesSorted()

      if (takenFiles.isNotEmpty() && notifyListeners) {
        onReplyFilesChanged()
      }

      return@Try takenFiles
    }
  }

  @Synchronized
  fun putFiles(files: List<ReplyFile>): Boolean {
    if (files.isEmpty()) {
      return true
    }

    var success = true

    files.forEach { replyFile ->
      val error = replyFile.markFilesAsNotTaken().errorOrNull()
      if (error == null) {
        return@forEach
      }

      Logger.e(TAG, "Failed to put file back", error)
      success = false
    }

    if (success) {
      onReplyFilesChanged()
    }

    return success
  }

  @Synchronized
  fun updateFileSelection(fileUuid: UUID, selected: Boolean, notifyListeners: Boolean): ModularResult<Boolean> {
    return Try {
      val replyFile = replyFiles
        .firstOrNull { replyFile -> replyFile.getReplyFileMeta().unwrap().fileUuid == fileUuid }

      if (replyFile == null) {
        return@Try false
      }

      if (replyFile.getReplyFileMeta().unwrap().isTaken()) {
        return@Try false
      }

      if (notifyListeners) {
        onReplyFilesChanged()
      }

      return@Try replyFile.updateFileSelection(selected).unwrap()
    }
  }

  @Synchronized
  fun updateFileSpoilerFlag(fileUuid: UUID, spoiler: Boolean, notifyListeners: Boolean): ModularResult<Boolean> {
    return Try {
      val replyFile = replyFiles
        .firstOrNull { replyFile -> replyFile.getReplyFileMeta().unwrap().fileUuid == fileUuid }

      if (replyFile == null) {
        return@Try false
      }

      if (replyFile.getReplyFileMeta().unwrap().isTaken()) {
        return@Try false
      }

      if (notifyListeners) {
        onReplyFilesChanged()
      }

      return@Try replyFile.updateFileSpoilerFlag(spoiler).unwrap()
    }
  }

  @Synchronized
  fun deleteFile(fileUuid: UUID, notifyListeners: Boolean): ModularResult<Unit> {
    return deleteFiles(listOf(fileUuid), notifyListeners)
  }

  @Synchronized
  fun deleteSelectedFiles(notifyListeners: Boolean): ModularResult<Unit> {
    return Try {
      val toDelete = mutableListOf<UUID>()

      iterateFilesOrdered { _, replyFile, replyFileMeta ->
        if (replyFileMeta.isTaken()) {
          return@iterateFilesOrdered
        }

        if (!replyFileMeta.selected) {
          return@iterateFilesOrdered
        }

        toDelete += replyFileMeta.fileUuid
      }

      deleteFiles(toDelete, notifyListeners).unwrap()
    }
  }

  @Synchronized
  fun deleteFiles(fileUuids: List<UUID>, notifyListeners: Boolean): ModularResult<Unit> {
    if (fileUuids.isEmpty()) {
      return value(Unit)
    }

    return Try {
      var deleted = false

      fileUuids.forEach { fileUuid ->
        val index = replyFiles
          .indexOfFirst { replyFile -> replyFile.getReplyFileMeta().unwrap().fileUuid == fileUuid }

        if (index < 0) {
          return@Try
        }

        val replyFile = replyFiles.removeAt(index)
        replyFile.deleteFromDisk()

        deleted = true
      }

      if (deleted && notifyListeners) {
        onReplyFilesChanged()
      }
    }
  }

  @Synchronized
  fun hasSelectedFiles(): ModularResult<Boolean> {
    return Try { replyFiles.any { replyFile -> replyFile.getReplyFileMeta().unwrap().selected } }
  }

  @Synchronized
  fun isSelected(fileUuid: UUID): ModularResult<Boolean> {
    return Try {
      for (replyFile in replyFiles) {
        val meta = replyFile.getReplyFileMeta().unwrap()

        if (meta.fileUuid == fileUuid) {
          if (meta.isTaken()) {
            return@Try false
          }

          return@Try meta.selected
        }
      }

      return@Try false
    }
  }

  @Synchronized
  fun isMarkedAsSpoiler(fileUuid: UUID): ModularResult<Boolean> {
    return Try {
      for (replyFile in replyFiles) {
        val meta = replyFile.getReplyFileMeta().unwrap()

        if (meta.fileUuid == fileUuid) {
          if (meta.isTaken()) {
            return@Try false
          }

          return@Try meta.spoiler
        }
      }

      return@Try false
    }
  }

  @Synchronized
  fun selectedFilesCount(): ModularResult<Int> {
    return Try {
      return@Try replyFiles.count { replyFile ->
        val replyMeta = replyFile.getReplyFileMeta().unwrap()

        if (replyMeta.isTaken()) {
          return@count false
        }

        return@count replyMeta.selected
      }
    }
  }

  @Synchronized
  fun totalFilesCount(): ModularResult<Int> {
    return Try {
      return@Try replyFiles.count { replyFile ->
        val replyMeta = replyFile.getReplyFileMeta().unwrap()

        if (replyMeta.isTaken()) {
          return@count false
        }

        return@count true
      }
    }
  }

  @Synchronized
  fun deleteAllFiles() {
    replyFiles.forEach { replyFile -> replyFile.deleteFromDisk() }
    replyFiles.clear()

    onReplyFilesChanged()
  }

  @Synchronized
  fun <T> mapOrderedNotNull(mapper: (Int, ReplyFile) -> T?): List<T> {
    ensureFilesSorted()
    return replyFiles.mapIndexedNotNull { index, replyFile -> mapper(index, replyFile) }
  }

  @Synchronized
  fun iterateFilesOrdered(iterator: (Int, ReplyFile, ReplyFileMeta) -> Unit) {
    ensureFilesSorted()
    replyFiles.forEachIndexed { index, replyFile ->
      val replyFileMeta = replyFile.getReplyFileMeta()
        .unwrap()

      iterator(index, replyFile, replyFileMeta)
    }
  }

  @Synchronized
  fun getReplyFileByFileUuid(fileUuid: UUID): ModularResult<ReplyFile?> {
    ensureFilesSorted()
    return Try {
      return@Try replyFiles.firstOrNull { replyFile ->
        return@firstOrNull replyFile.getReplyFileMeta().unwrap().fileUuid == fileUuid
      }
    }
  }

  @Synchronized
  fun reloadAllFilesFromDisk(
    attachFilesDir: File,
    attachFilesMetaDir: File,
    mediaPreviewsCacheDir: File
  ): ModularResult<Unit> {
    BackgroundUtils.ensureBackgroundThread()

    return Try {
      val attachFiles = attachFilesDir.listFiles()
        ?: emptyArray()

      if (attachFiles.isEmpty()) {
        return@Try
      }

      val attachFileMetas = attachFilesMetaDir.listFiles()
        ?.associateBy { file -> file.absolutePath }
        ?: emptyMap()

      val previewFiles = mediaPreviewsCacheDir.listFiles()
        ?.associateBy { file -> file.absolutePath }
        ?: emptyMap()

      val newAttachFiles = try {
        addAttachFiles(attachFiles, previewFiles, attachFileMetas).unwrap()
      } catch (error: Throwable) {
        Logger.e(TAG, "reloadAllFilesFromDisk() failure, removing all files", error)

        attachFilesDir.listFiles()?.forEach { attachFile -> attachFile.delete() }
        attachFilesMetaDir.listFiles()?.forEach { attachFilesMeta -> attachFilesMeta.delete() }
        mediaPreviewsCacheDir.listFiles()?.forEach { mediaPreviewFile -> mediaPreviewFile.delete() }

        replyFiles.clear()
        throw error
      }

      replyFiles.clear()
      replyFiles.addAll(newAttachFiles)

      ensureFilesSorted()
      onReplyFilesChanged()
    }
  }

  private fun addAttachFiles(
    attachFiles: Array<out File>,
    previewFiles: Map<String, File>,
    attachFileMetas: Map<String, File>
  ): ModularResult<List<ReplyFile>> {
    BackgroundUtils.ensureBackgroundThread()

    return Try {
      val processedMetaFiles = mutableSetOf<String>()
      val newAttachFiles = mutableListOf<ReplyFile>()
      val allPreviewFiles = previewFiles.toMutableMap()

      attachFiles.forEach { attachFile ->
        if (!attachFile.exists() || !attachFile.canRead()) {
          Logger.e(TAG, "addAttachFiles() attachFile does not exist or cannot be read, deleting files")

          attachFile.delete()
          return@forEach
        }

        val uuid = ReplyManager.extractUuidOrNull(attachFile.name)?.toString()
        if (uuid == null) {
          Logger.e(TAG, "addAttachFiles() Failed to extract uuid from file with name '${attachFile.name}'")
          attachFile.delete()
          return@forEach
        }

        val fileMetaName = ReplyManager.getMetaFileName(uuid)
        val previewFileName = ReplyManager.getPreviewFileName(uuid)

        val fileMeta = attachFileMetas.entries
          .firstOrNull { (fileMetaPath, _) -> fileMetaPath.endsWith(fileMetaName) }
          ?.value

        val previewFileMaybe = allPreviewFiles.entries
          .firstOrNull { (previewFilePath, _) -> previewFilePath.endsWith(previewFileName) }
          ?.value

        if (fileMeta == null) {
          Logger.e(TAG, "addAttachFiles() couldn't find fileMeta for fileMetaName='$fileMetaName', " +
            "deleting files")

          attachFile.delete()
          previewFileMaybe?.delete()

          return@forEach
        }

        if (!fileMeta.exists() || !fileMeta.canRead()) {
          Logger.e(TAG, "addAttachFiles() fileMeta does not exist or cannot be read, deleting files")

          attachFile.delete()
          fileMeta.delete()
          previewFileMaybe?.delete()

          return@forEach
        }

        val previewFile = previewFileMaybe
          ?: File(appConstants.mediaPreviewsDir, previewFileName)

        if (!previewFile.exists()) {
          previewFile.createNewFile()
        }

        val replyFile = ReplyFile(
          gson,
          attachFile,
          fileMeta,
          previewFile
        )

        val replyFileMeta = replyFile.getReplyFileMeta()
          .safeUnwrap { error ->
            Logger.e(TAG, "addAttachFiles() getReplyFileMeta() error", error)

            attachFile.delete()
            fileMeta.delete()
            previewFile.delete()

            return@forEach
          }

        if (replyFileMeta.isTaken()) {
          Logger.e(TAG, "addAttachFiles() replyFileMeta ($replyFileMeta) is taken by " +
            "${replyFileMeta.fileTakenBy}, deleting files")

          attachFile.delete()
          fileMeta.delete()
          previewFile.delete()

          return@forEach
        }

        if (!replyFileMeta.isValidMeta()) {
          Logger.e(TAG, "addAttachFiles() replyFileMeta ($replyFileMeta) is not valid, deleting files")

          attachFile.delete()
          fileMeta.delete()
          previewFile.delete()

          return@forEach
        }

        processedMetaFiles += fileMeta.absolutePath
        newAttachFiles += replyFile

        previewFile.let { previewFileOnDisk -> allPreviewFiles.remove(previewFileOnDisk.absolutePath) }
      }

      if (processedMetaFiles.isNotEmpty()) {
        attachFileMetas.entries.forEach { (metaPath, metaFile) ->
          if (metaPath in processedMetaFiles) {
            return@forEach
          }

          Logger.d(TAG, "Deleting metaFile='${metaFile.absolutePath}' " +
            "because it wasn't processed normally")
          metaFile.delete()
        }
      }

      if (allPreviewFiles.isNotEmpty()) {
        allPreviewFiles.entries.forEach { (previewFilePath, previewFile) ->
          Logger.d(TAG, "Deleting previewFile='${previewFile.absolutePath}' " +
            "because it wasn't processed normally")
          previewFile.delete()
        }
      }

      return@Try newAttachFiles
        .sortedBy { newAttachFile -> newAttachFile.getReplyFileMeta().unwrap().addedOn }
    }
  }

  @Synchronized
  fun addNewReplyFile(replyFile: ReplyFile): Boolean {
    if (!replyFile.fileOnDisk.exists()) {
      Logger.e(TAG, "addNewReplyFile() fileOnDisk does not exist (file='${replyFile.fileOnDisk}')")
      return false
    }

    if (!replyFile.fileOnDisk.canRead()) {
      Logger.e(TAG, "addNewReplyFile() fileOnDisk cannot be read (file='${replyFile.fileOnDisk}')")
      return false
    }

    if (!replyFile.fileMetaOnDisk.exists()) {
      Logger.e(TAG, "addNewReplyFile() fileMetaOnDisk does not exist (file='${replyFile.fileMetaOnDisk}')")
      return false
    }

    if (!replyFile.fileMetaOnDisk.canRead()) {
      Logger.e(TAG, "addNewReplyFile() fileMetaOnDisk cannot be read (file='${replyFile.fileMetaOnDisk}')")
      return false
    }

    val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
      Logger.e(TAG, "addNewReplyFile() getReplyFileMeta() error", error)
      return false
    }

    if (!replyFileMeta.isValidMeta()) {
      Logger.e(TAG, "addNewReplyFile() isValidMeta() is false, meta=${replyFileMeta}")
      return false
    }

    if (replyFileMeta.fileTakenBy != null) {
      Logger.e(TAG, "addNewReplyFile() fileTakenBy != null (fileTakenBy='${replyFileMeta.fileTakenBy}')")
      return false
    }

    if (replyFile.fileOnDisk.name != ReplyManager.getFileName(replyFileMeta.fileUuidString)) {
      Logger.e(TAG, "addNewReplyFile() fileOnDisk bad name " +
        "(fileOnDisk.name='${replyFile.fileOnDisk.name}')")
      return false
    }

    if (replyFile.fileMetaOnDisk.name != ReplyManager.getMetaFileName(replyFileMeta.fileUuidString)) {
      Logger.e(TAG, "addNewReplyFile() fileMetaOnDisk bad name " +
        "(fileMetaOnDisk.name='${replyFile.fileMetaOnDisk.name}')")
      return false
    }

    replyFiles += replyFile
    ensureFilesSorted()

    return true
  }

  private fun ensureFilesSorted() {
    var prevAddedOn = Long.MIN_VALUE

    replyFiles.forEach { replyFile ->
      val addedOn = replyFile.getReplyFileMeta().unwrap().addedOn
      check(addedOn >= prevAddedOn) { "Files not sorted! addedOn='$addedOn', prevAddedOn='$prevAddedOn'" }

      prevAddedOn = addedOn
    }
  }

  companion object {
    private const val TAG = "ReplyFiles"
  }
}