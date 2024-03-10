/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.manager

import android.graphics.Bitmap
import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter
import com.github.k1rakishou.chan.features.reply.data.Reply
import com.github.k1rakishou.chan.features.reply.data.ReplyDataJson
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.features.reply.data.ReplyFilesStorage
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Manages replies.
 */
class ReplyManager(
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val appConstants: AppConstants,
  private val moshi: Moshi,
  commonGson: Gson
) {
  private val filesLoaded = AtomicBoolean(false)
  private val filesLoadedLatch = CountDownLatch(1)
  private val filesLoadedInitializer = SuspendableInitializer<Unit>("filesLoadedInitializer")

  private val gson = commonGson.newBuilder()
    .excludeFieldsWithoutExposeAnnotation()
    .setVersion(REPLY_FILE_META_GSON_VERSION)
    .create()

  @GuardedBy("itself")
  private val drafts = mutableMapWithCap<ChanDescriptor, Reply>(64)

  private val replyFilesStorage by lazy { ReplyFilesStorage(gson, appConstants) }

  init {
    applicationVisibilityManager.addListener { applicationVisibility ->
      if (applicationVisibility.isInBackground()) {
        val duration = measureTime { persistAllDrafts() }
        Logger.d(TAG, "persistDrafts() took $duration")
      }
    }
  }

  suspend fun awaitUntilFilesAreLoaded() {
    if (filesLoadedInitializer.isInitialized()) {
      return
    }

    Logger.d(TAG, "ReplyManager is not ready yet, waiting...")
    val duration = measureTime { filesLoadedInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "ReplyManager initialization completed, took $duration")
  }

  fun notifyReplyFilesChanged(fileUuid: UUID) {
    ensureFilesLoaded()
    replyFilesStorage.notifyReplyFilesChanged(fileUuid)
  }

  fun reloadReplyManagerStateFromDisk(appConstants: AppConstants): ModularResult<Unit> {
    if (!filesLoaded.compareAndSet(false, true)) {
      filesLoadedLatch.await()

      if (!filesLoadedInitializer.isInitialized()) {
        filesLoadedInitializer.initWithValue(Unit)
      }

      return ModularResult.value(Unit)
    }

    val restoreDraftsDuration = measureTime { restoreDrafts() }
    Logger.d(TAG, "reloadFilesFromDisk() restoreDrafts() took $restoreDraftsDuration")

    val (result, reloadAllFilesFromDiskDuration) = measureTimedValue {
      replyFilesStorage.reloadAllFilesFromDisk(
        appConstants.attachFilesDir,
        appConstants.attachFilesMetaDir,
        appConstants.mediaPreviewsDir
      )
    }
    Logger.d(TAG, "reloadFilesFromDisk() reloadAllFilesFromDisk() took $reloadAllFilesFromDiskDuration")

    filesLoadedInitializer.initWithValue(Unit)
    filesLoadedLatch.countDown()

    if (result is ModularResult.Error) {
      Logger.e(TAG, "reloadAllFilesFromDisk() error, clearing all files", result.error)
      replyFilesStorage.deleteAllFiles()
      return result
    }

    return ModularResult.value(Unit)
  }

  fun addNewReplyFileIntoStorage(replyFile: ReplyFile, notifyListeners: Boolean): Boolean {
    ensureFilesLoaded()
    return replyFilesStorage.addNewReplyFile(replyFile, notifyListeners)
  }

  fun updateFileSelection(fileUuid: UUID, selected: Boolean, notifyListeners: Boolean): ModularResult<Boolean> {
    ensureFilesLoaded()
    return replyFilesStorage.updateFileSelection(fileUuid, selected, notifyListeners)
  }

  fun updateFileSpoilerFlag(fileUuid: UUID, spoiler: Boolean, notifyListeners: Boolean): ModularResult<Boolean> {
    ensureFilesLoaded()
    return replyFilesStorage.updateFileSpoilerFlag(fileUuid, spoiler, notifyListeners)
  }

  fun updateFileName(fileUuid: UUID, newFileName: String, notifyListeners: Boolean): ModularResult<Boolean> {
    ensureFilesLoaded()
    return replyFilesStorage.updateFileName(fileUuid, newFileName, notifyListeners)
  }

  fun updatePreviewFileOnDisk(fileUuid: UUID, previewBitmap: Bitmap): ModularResult<Boolean> {
    ensureFilesLoaded()
    val previewFile = File(
      appConstants.mediaPreviewsDir,
      getPreviewFileName(fileUuid.toString())
    )

    return replyFilesStorage.updatePreviewFileOnDisk(fileUuid, previewFile, previewBitmap)
      .onError { previewFile.delete() }
  }

  fun deleteFile(fileUuid: UUID, notifyListeners: Boolean): ModularResult<Unit> {
    ensureFilesLoaded()
    return replyFilesStorage.deleteFile(fileUuid, notifyListeners)
  }

  fun deleteSelectedFiles(notifyListeners: Boolean): ModularResult<Unit> {
    ensureFilesLoaded()
    return replyFilesStorage.deleteSelectedFiles(notifyListeners)
  }

  fun hasSelectedFiles(): ModularResult<Boolean> {
    ensureFilesLoaded()
    return replyFilesStorage.hasSelectedFiles()
  }

  fun isSelected(fileUuid: UUID): ModularResult<Boolean> {
    ensureFilesLoaded()
    return replyFilesStorage.isSelected(fileUuid)
  }

  fun isMarkedAsSpoiler(fileUuid: UUID): ModularResult<Boolean> {
    ensureFilesLoaded()
    return replyFilesStorage.isMarkedAsSpoiler(fileUuid)
  }

  fun selectedFilesCount(): ModularResult<Int> {
    ensureFilesLoaded()
    return replyFilesStorage.selectedFilesCount()
  }

  fun totalFilesCount(): ModularResult<Int> {
    ensureFilesLoaded()
    return replyFilesStorage.totalFilesCount()
  }

  fun takeSelectedFiles(chanDescriptor: ChanDescriptor): ModularResult<Boolean> {
    ensureFilesLoaded()

    return Try {
      return@Try readReply(chanDescriptor) { reply ->
        val selectedFiles = replyFilesStorage.selectedFilesCount().unwrap()
        val takenFiles = replyFilesStorage.takeSelectedFiles(chanDescriptor).unwrap()

        if (takenFiles.size != selectedFiles) {
          Logger.error(TAG) {
            "takeSelectedFiles($chanDescriptor) failed to take some of selected files, " +
              "takenFiles.size: ${takenFiles.size}, selectedFiles: $selectedFiles"
          }

          return@readReply false
        }

        reply.putReplyFiles(takenFiles)
        return@readReply true
      }
    }
  }

  fun restoreFiles(chanDescriptor: ChanDescriptor) {
    ensureFilesLoaded()

    readReply(chanDescriptor) { reply ->
      if (!replyFilesStorage.restoreFiles(reply.getAndConsumeFiles())) {
        Logger.e(TAG, "replyFiles.putFiles() Not all files were put back")
      }
    }
  }

  fun iterateFilesOrdered(iterator: (Int, ReplyFile, ReplyFileMeta) -> Unit) {
    replyFilesStorage.iterateFilesOrdered(iterator)
  }

  fun iterateNonTakenFilesOrdered(iterator: (Int, ReplyFile, ReplyFileMeta) -> Unit) {
    replyFilesStorage.iterateFilesOrdered { order, replyFile, replyFileMeta ->
      if (replyFileMeta.isTaken()) {
        return@iterateFilesOrdered
      }

      iterator(order, replyFile, replyFileMeta)
    }
  }

  fun iterateSelectedFilesOrdered(iterator: (Int, ReplyFile, ReplyFileMeta) -> Unit) {
    replyFilesStorage.iterateFilesOrdered { order, replyFile, replyFileMeta ->
      if (!replyFileMeta.selected) {
        return@iterateFilesOrdered
      }

      iterator(order, replyFile, replyFileMeta)
    }
  }

  fun getSelectedFilesOrdered(): List<ReplyFile> {
    val files = mutableListOf<ReplyFile>()

    replyFilesStorage.iterateFilesOrdered { i, replyFile, replyFileMeta ->
      if (replyFileMeta.selected) {
        files += replyFile
      }
    }

    return files
  }

  fun getReplyFileByFileUuid(fileUuid: UUID): ModularResult<ReplyFile?> {
    ensureFilesLoaded()
    return replyFilesStorage.getReplyFileByFileUuid(fileUuid)
  }

  fun cleanupFiles(chanDescriptor: ChanDescriptor, notifyListeners: Boolean) {
    ensureFilesLoaded()

    readReply(chanDescriptor) { reply ->
      val fileUuids = reply.cleanupFiles()
        .onError { error -> Logger.e(TAG, "reply.cleanupFiles() error", error) }
        .valueOrNull()

      if (fileUuids == null || fileUuids.isEmpty()) {
        return@readReply
      }

      replyFilesStorage.deleteFiles(fileUuids, notifyListeners)
        .onError { error -> Logger.e(TAG, "replyFilesStorage.deleteFiles($fileUuids) error", error) }
        .ignore()
    }
  }

  fun <T> mapOrderedNotNull(mapper: (Int, ReplyFile) -> T?): List<T> {
    ensureFilesLoaded()
    return replyFilesStorage.mapOrderedNotNull(mapper)
  }

  fun listenForReplyFilesUpdates(): Flow<Collection<UUID>> {
    return replyFilesStorage.listenForReplyFilesUpdates()
  }

  fun getReplyOrCreateNew(chanDescriptor: ChanDescriptor): Reply {
    ensureFilesLoaded()

    val reply = synchronized(drafts) {
      var reply = drafts[chanDescriptor]
      if (reply == null) {
        reply = Reply(chanDescriptor)
        drafts[chanDescriptor] = reply
      }

      return@synchronized reply
    }

    if (reply.postName.isEmpty()) {
      reply.postName = ChanSettings.postDefaultName.get()
    }

    return reply
  }

  fun getReplyOrNull(chanDescriptor: ChanDescriptor): Reply? {
    ensureFilesLoaded()
    return synchronized(drafts) { drafts[chanDescriptor] }
  }

  fun containsReply(chanDescriptor: ChanDescriptor): Boolean {
    ensureFilesLoaded()
    return synchronized(drafts) { drafts.containsKey(chanDescriptor) }
  }

  fun <T : Any?> readReply(chanDescriptor: ChanDescriptor, reader: (Reply) -> T): T {
    ensureFilesLoaded()
    return reader(getReplyOrCreateNew(chanDescriptor))
  }

  fun createNewEmptyAttachFile(
    uniqueFileName: UniqueFileName,
    originalFileName: String,
    addedOn: Long
  ): ReplyFile? {
    BackgroundUtils.ensureBackgroundThread()

    val attachFile = File(
      appConstants.attachFilesDir,
      uniqueFileName.fullFileName
    )

    val attachFileMeta = File(
      appConstants.attachFilesMetaDir,
      uniqueFileName.fullFileMetaName
    )

    try {
      if (!attachFile.exists()) {
        if (!attachFile.createNewFile()) {
          throw IOException("Failed to create attach file: " + attachFile.absolutePath)
        }
      }

      if (!attachFileMeta.exists()) {
        if (!attachFileMeta.createNewFile()) {
          throw IOException("Failed to create attach file meta: " + attachFileMeta.absolutePath)
        }
      }

      val replyFile = ReplyFile(
        gson = gson,
        fileOnDisk = attachFile,
        fileMetaOnDisk = attachFileMeta,
        previewFileOnDisk = null
      )

      replyFile.storeFileMetaInfo(
        ReplyFileMeta(
          fileUuidStringNullable = uniqueFileName.fileUuid.toString(),
          originalFileNameNullable = originalFileName,
          fileNameNullable = originalFileName,
          addedOnNullable = addedOn
        )
      ).unwrap()

      return replyFile
    } catch (error: Throwable) {
      Logger.e(TAG, "Failed to create new empty attach file ${uniqueFileName.fullFileName}", error)

      attachFile.delete()
      attachFileMeta.delete()

      return null
    }
  }

  fun generateUniqueFileName(appConstants: AppConstants): UniqueFileName {
    BackgroundUtils.ensureBackgroundThread()

    val attachFilesDir = appConstants.attachFilesDir
    val attachFilesMetaDir = appConstants.attachFilesMetaDir

    val filesInDir = attachFilesDir.listFiles()
    val metaFilesInDir = attachFilesMetaDir.listFiles()

    val allFileNamesSet = filesInDir
      ?.map { file -> file.absolutePath }
      ?.toSet()
      ?: emptySet()

    val allMetaFileNamesSet = metaFilesInDir
      ?.map { file -> file.absolutePath }
      ?.toSet()
      ?: emptySet()

    while (true) {
      val uuid = UUID.randomUUID()
      val fileName = getFileName(uuid.toString())
      val metaFileName = getMetaFileName(uuid.toString())
      val previewFileName = getPreviewFileName(uuid.toString())

      if ((filesInDir == null || filesInDir.isEmpty())
        && (metaFilesInDir == null || metaFilesInDir.isEmpty())) {
        return UniqueFileName(uuid, fileName, metaFileName, previewFileName)
      }

      if (fileName in allFileNamesSet) {
        continue
      }

      if (metaFileName in allMetaFileNamesSet) {
        continue
      }

      return UniqueFileName(
        fileUuid = uuid,
        fullFileName = fileName,
        fullFileMetaName = metaFileName,
        previewFileName = previewFileName
      )
    }
  }

  fun getNewImageName(
    currentFileName: String,
    newType: ImageReencodingPresenter.ReencodeType = ImageReencodingPresenter.ReencodeType.AS_IS
  ): String {
    var currentExt = StringUtils.extractFileNameExtension(currentFileName)
    currentExt = if (currentExt == null) {
      ""
    } else {
      ".$currentExt"
    }

    return when (newType) {
      ImageReencodingPresenter.ReencodeType.AS_PNG -> System.currentTimeMillis().toString() + ".png"
      ImageReencodingPresenter.ReencodeType.AS_JPEG -> System.currentTimeMillis().toString() + ".jpg"
      ImageReencodingPresenter.ReencodeType.AS_IS -> System.currentTimeMillis().toString() + currentExt
      else -> System.currentTimeMillis().toString() + currentExt
    }
  }

  fun deleteCachedDraftFromDisk(chanDescriptor: ChanDescriptor) {
    val draftOnDisk = File(appConstants.replyDraftsDir, chanDescriptor.replyDraftFileName())
    draftOnDisk.delete()

    Logger.d(TAG, "deleteCachedDraftFromDisk($chanDescriptor), draftOnDisk='${draftOnDisk.absolutePath}'")
  }

  private fun persistAllDrafts() {
    synchronized(drafts) {
      val draftsSorted = drafts
        .entries
        .sortedByDescending { (_, reply) -> reply.lastUpdatedAt }
        .take(MAX_DRAFTS_PERSISTED)

      if (draftsSorted.isEmpty()) {
        Logger.d(TAG, "persistDrafts() drafts are empty")
        return
      }

      var persistedCount = 0
      var deletedCount = 0

      Logger.d(TAG, "persistDrafts() persisting ${draftsSorted.size} out of ${drafts.size}")

      for ((chanDescriptor, reply) in draftsSorted) {
        if (persistDraft(chanDescriptor, reply)) {
          ++persistedCount
        }
      }

      val draftDescriptorsSet = draftsSorted.toHashSetBy { (chanDescriptor, _) -> chanDescriptor }
      Logger.d(TAG, "persistDrafts() deleting old drafts")

      // Delete old drafts that are not included in MAX_DRAFTS_PERSISTED
      for ((chanDescriptor, _) in drafts.entries) {
        try {
          if (chanDescriptor in draftDescriptorsSet) {
            continue
          }

          val draftFile = File(appConstants.replyDraftsDir, chanDescriptor.replyDraftFileName())
          draftFile.delete()

          ++deletedCount
        } catch (error: Throwable) {
          Logger.e(TAG, "persistDrafts() failed to remove old reply draft for descriptor ${chanDescriptor}", error)
        }
      }

      Logger.debug(TAG) {
        "persistDrafts() done " +
          "persistedCount: $persistedCount, " +
          "deletedCount: $deletedCount, " +
          "total: ${draftsSorted.size}"
      }
    }
  }

  fun persistDraft(
    chanDescriptor: ChanDescriptor,
    reply: Reply
  ): Boolean {
    try {
      if (!reply.dirty) {
        return false
      }

      if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
        return false
      }

      val draftFile = File(appConstants.replyDraftsDir, chanDescriptor.replyDraftFileName())

      val replyDataJson = reply.toReplyDataJson()
      if (replyDataJson == null) {
        draftFile.delete()
        return false
      }

      val replyDataJsonString = moshi
        .adapter<ReplyDataJson>(ReplyDataJson::class.java)
        .toJson(replyDataJson)

      draftFile.writeText(replyDataJsonString)
      return true
    } catch (error: Throwable) {
      Logger.e(TAG, "persistDrafts() failed to store reply for descriptor ${chanDescriptor}", error)
      return false
    }
  }

  private fun restoreDrafts() {
    synchronized(drafts) {
      val draftFiles = appConstants.replyDraftsDir.listFiles()
      if (draftFiles.isNullOrEmpty()) {
        Logger.d(TAG, "restoreDrafts() draftFiles is empty")
        return
      }

      for (draftFile in draftFiles) {
        try {
          val replyDataJson = draftFile.source().buffer().use { bufferedSource ->
            return@use moshi
              .adapter<ReplyDataJson>(ReplyDataJson::class.java)
              .fromJson(bufferedSource)
          }

          if (replyDataJson == null || replyDataJson.isEmpty()) {
            continue
          }

          val reply = replyDataJson.toReplyOrNull()
            ?: continue

          drafts[reply.chanDescriptor] = reply
        } catch (error: Throwable) {
          Logger.e(TAG, "restoreDrafts() failed restore draft for file ${draftFile.absolutePath}", error)
          draftFile.delete()
        }
      }

      Logger.d(TAG, "restoreDrafts() done, draftsCount=${drafts.size}")
    }
  }

  private fun ChanDescriptor.replyDraftFileName(): String {
    return when (this) {
      is ChanDescriptor.CompositeCatalogDescriptor -> error("Cannot use CompositeCatalogDescriptor here")
      is ChanDescriptor.CatalogDescriptor -> "${siteName()}_${boardCode()}.json"
      is ChanDescriptor.ThreadDescriptor -> "${siteName()}_${boardCode()}_${threadNo}.json"
      else -> error("Unexpected descriptor type: ${javaClass.simpleName}")
    }
  }

  private fun ensureFilesLoaded() {
    if (!filesLoaded.get()) {
      if (!filesLoadedLatch.await(30, TimeUnit.SECONDS)) {
        error("Deadlock!")
      }
    }
  }

  data class UniqueFileName(
    val fileUuid: UUID,
    val fullFileName: String,
    val fullFileMetaName: String,
    val previewFileName: String
  )

  companion object {
    private const val TAG = "ReplyManager"
    private const val REPLY_FILE_META_GSON_VERSION = 1.0
    private const val MAX_DRAFTS_PERSISTED = 16

    const val ATTACH_FILE_NAME = "attach_file"
    const val ATTACH_FILE_META_NAME = "attach_file_meta"
    const val PREVIEW_FILE_NAME = "preview"

    fun getFileName(uuid: String) = "${ATTACH_FILE_NAME}_$uuid"
    fun getMetaFileName(uuid: String) = "${ATTACH_FILE_META_NAME}_$uuid"
    fun getPreviewFileName(uuid: String) = "${PREVIEW_FILE_NAME}_$uuid"

    fun extractUuidOrNull(fileName: String): UUID? {
      val attachFileName = "${ATTACH_FILE_NAME}_"
      if (fileName.startsWith(attachFileName)) {
        return uuidFromStringOrNull(fileName.substringAfter(attachFileName))
      }

      val attachFileMetaName = "${ATTACH_FILE_META_NAME}_"
      if (fileName.startsWith(attachFileMetaName)) {
        return uuidFromStringOrNull(fileName.substringAfter(attachFileMetaName))
      }

      return null
    }

    private fun uuidFromStringOrNull(uuidString: String): UUID? {
      return try {
        UUID.fromString(uuidString)
      } catch (error: Throwable) {
        Logger.e(TAG, "Bad UUID: '$uuidString'")
        return null
      }
    }
  }
}