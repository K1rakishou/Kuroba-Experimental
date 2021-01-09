package com.github.k1rakishou.chan.features.reply

import androidx.exifinterface.media.ExifInterface
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.base.ThrottlingCoroutineExecutor
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.IReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.features.reply.data.ReplyNewAttachable
import com.github.k1rakishou.chan.features.reply.data.TooManyAttachables
import com.github.k1rakishou.chan.features.reply.epoxy.EpoxyReplyFileView
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class ReplyLayoutFilesAreaPresenter(
  private val appConstants: AppConstants,
  private val replyManager: ReplyManager,
  private val boardManager: BoardManager,
  private val imageLoaderV2: ImageLoaderV2,
  private val postingLimitationsInfoManager: PostingLimitationsInfoManager,
  private val imagePickHelper: ImagePickHelper
) : BasePresenter<ReplyLayoutFilesAreaView>() {
  private val pickFilesExecutor = RendezvousCoroutineExecutor(scope)
  private val refreshFilesExecutor = ThrottlingCoroutineExecutor(scope)
  private val fileChangeExecutor = SerializedCoroutineExecutor(scope)
  private val state = MutableStateFlow(ReplyLayoutFilesState())
  private var boundChanDescriptor: ChanDescriptor? = null

  fun listenForStateUpdates(): Flow<ReplyLayoutFilesState> = state.asStateFlow()

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    this.boundChanDescriptor = chanDescriptor

    scope.launch {
      imagePickHelper.listenForNewPickedFiles()
        .collect { refreshAttachedFiles() }
    }

    scope.launch {
      replyManager.listenForReplyFilesUpdates()
        .collect { refreshAttachedFiles() }
    }

    reloadFilesFromDiskAndInitState(chanDescriptor)
  }

  fun unbindChanDescriptor() {
    this.boundChanDescriptor = null
  }

  fun pickLocalFile(showFilePickerChooser: Boolean) {
    pickFilesExecutor.post {
      handleStateUpdate {
        val chanDescriptor = boundChanDescriptor
          ?: return@handleStateUpdate

        val job = SupervisorJob()
        val cancellationFunc = { job.cancel() }

        val input = LocalFilePicker.LocalFilePickerInput(
          replyChanDescriptor = chanDescriptor,
          clearLastRememberedFilePicker = showFilePickerChooser,
          showLoadingView = {
            withView {
              showLoadingView(
                cancellationFunc,
                R.string.decoding_reply_file_preview
              )
            }
          },
          hideLoadingView = { withView { hideLoadingView() } }
        )

        val pickedFileResult = withContext(job) { imagePickHelper.pickLocalFile(input) }
          .finally { withView { hideLoadingView() } }
          .safeUnwrap { error ->
            Logger.e(TAG, "imagePickHelper.pickLocalFile($chanDescriptor) error", error)
            withView { showGenericErrorToast(error.errorMessageOrClassName()) }
            return@handleStateUpdate
          }

        if (pickedFileResult is PickedFile.Failure) {
          Logger.e(
            TAG, "pickNewLocalFile() error, " +
              "pickedFileResult=${pickedFileResult.reason.errorMessageOrClassName()}"
          )

          withView { showFilePickerErrorToast(pickedFileResult.reason) }
          return@handleStateUpdate
        }

        val replyFile = (pickedFileResult as PickedFile.Result).replyFiles.first()

        val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(
            TAG,
            "imagePickHelper.pickLocalFile($chanDescriptor) getReplyFileMeta() error",
            error
          )
          return@handleStateUpdate
        }

        val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
        if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
          replyManager.updateFileSelection(replyFileMeta.fileUuid, true)
        }

        Logger.d(TAG, "pickNewLocalFile() success")
        refreshAttachedFiles()
      }
    }
  }

  fun pickRemoteFile(url: String) {
    pickFilesExecutor.post {
      handleStateUpdate {
        val chanDescriptor = boundChanDescriptor
          ?: return@handleStateUpdate

        val job = SupervisorJob()
        val cancellationFunc = { job.cancel() }

        val input = RemoteFilePicker.RemoteFilePickerInput(
          replyChanDescriptor = chanDescriptor,
          imageUrl = url,
          showLoadingView = { textId -> withView { showLoadingView(cancellationFunc, textId) } },
          hideLoadingView = { withView { hideLoadingView() } }
        )

        val pickedFileResult = withContext(job) { imagePickHelper.pickRemoteFile(input) }
          .finally { withView { hideLoadingView() } }
          .safeUnwrap { error ->
            Logger.e(TAG, "imagePickHelper.pickRemoteFile($chanDescriptor) error", error)
            withView { showGenericErrorToast(error.errorMessageOrClassName()) }
            return@handleStateUpdate
          }

        if (pickedFileResult is PickedFile.Failure) {
          Logger.e(
            TAG, "pickRemoteFile() error, " +
              "pickedFileResult=${pickedFileResult.reason.errorMessageOrClassName()}"
          )

          withView { showFilePickerErrorToast(pickedFileResult.reason) }
          return@handleStateUpdate
        }

        val replyFile = (pickedFileResult as PickedFile.Result).replyFiles.first()

        val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(
            TAG,
            "imagePickHelper.pickRemoteFile($chanDescriptor) getReplyFileMeta() error",
            error
          )
          return@handleStateUpdate
        }

        val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
        if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
          replyManager.updateFileSelection(replyFileMeta.fileUuid, true)
        }

        Logger.d(TAG, "pickRemoteFile() success")
        refreshAttachedFiles()
      }
    }
  }

  fun hasSelectedFiles(): Boolean = replyManager.hasSelectedFiles().unwrap()
  fun selectedFilesCount(): Int = replyManager.selectedFilesCount().unwrap()

  private fun boardSupportsSpoilers(): Boolean {
    val chanDescriptor = boundChanDescriptor
      ?: return false

    return boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?.spoilers
      ?: return false
  }

  fun clearSelection() {
    fileChangeExecutor.post {
      replyManager.clearFilesSelection().safeUnwrap { error ->
        Logger.e(TAG, "clearSelection() error", error)
        return@post
      }

      refreshAttachedFiles()
    }
  }

  fun updateFileSelection(fileUuid: UUID) {
    fileChangeExecutor.post {
      handleStateUpdate {
        val nowSelected = replyManager.isSelected(fileUuid).unwrap().not()

        replyManager.updateFileSelection(fileUuid, nowSelected)
          .safeUnwrap { error ->
            Logger.e(TAG, "updateFileSelection($fileUuid, $nowSelected) error", error)
            return@handleStateUpdate
          }

        refreshAttachedFiles(debounceTime = FILE_SELECTION_UPDATE_DEBOUNCE_TIME)
      }
    }
  }

  fun updateFileSpoilerFlag(fileUuid: UUID) {
    fileChangeExecutor.post {
      handleStateUpdate {
        val nowMarkedAsSpoiler = replyManager.isMarkedAsSpoiler(fileUuid).unwrap().not()

        replyManager.updateFileSpoilerFlag(fileUuid, nowMarkedAsSpoiler)
          .safeUnwrap { error ->
            Logger.e(TAG, "updateFileSpoilerFlag($fileUuid, $nowMarkedAsSpoiler) error", error)
            return@handleStateUpdate
          }

        refreshAttachedFiles()
      }
    }
  }

  fun deleteFiles(fileUuid: UUID) {
    fileChangeExecutor.post {
      handleStateUpdate {
        replyManager.deleteFile(fileUuid)
          .safeUnwrap { error ->
            Logger.e(TAG, "deleteFile($fileUuid) error", error)
            return@handleStateUpdate
          }

        refreshAttachedFiles()
      }
    }
  }

  fun deleteSelectedFiles() {
    fileChangeExecutor.post {
      handleStateUpdate {
        replyManager.deleteSelectedFiles()
          .safeUnwrap { error ->
            Logger.e(TAG, "deleteSelectedFiles() error", error)
            return@handleStateUpdate
          }

        refreshAttachedFiles()
      }
    }
  }

  fun hasAttachedFiles(): Boolean {
    return state.value.attachables.any { replyAttachable -> replyAttachable is ReplyFileAttachable }
  }

  fun refreshAttachedFiles(debounceTime: Long = REFRESH_FILES_DEBOUNCE_TIME) {
    refreshFilesExecutor.post(debounceTime) {
      handleStateUpdate {
        val chanDescriptor = boundChanDescriptor
          ?: return@handleStateUpdate

        val attachables = enumerateReplyAttachables(chanDescriptor).unwrap()

        val oldState = state.value
        val newState = ReplyLayoutFilesState(attachables)
        state.value = newState

        if (oldState != newState) {
          withView {
            val selectedFilesCount = state.value.attachables.count { replyAttachable ->
              replyAttachable is ReplyFileAttachable && replyAttachable.selected
            }
            val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)

            if (maxAllowedFilesPerPost != null) {
              updateSendButtonState(selectedFilesCount, maxAllowedFilesPerPost)
            }
          }

          scope.launch {
            // Wait some time for the reply files recycler to get laid out, otherwise reply layout
            // will have outdated height and requestReplyLayoutWrappingModeUpdate won't apply
            // correct paddings.
            delay(REFRESH_FILES_APPROX_DURATION)

            withView { requestReplyLayoutWrappingModeUpdate() }
          }
        }
      }
    }
  }

  private suspend fun reloadFilesFromDiskAndInitState(chanDescriptor: ChanDescriptor) {
    handleStateUpdate {
      withContext(Dispatchers.IO) { replyManager.reloadFilesFromDisk(appConstants) }
        .unwrap()

      replyManager.iterateFilesOrdered { _, replyFile ->
        val replyFileMeta = replyFile.getReplyFileMeta().unwrap()
        if (replyFileMeta.selected) {
          replyManager.updateFileSelection(replyFileMeta.fileUuid, true)
        }
      }

      val newAttachFiles = enumerateReplyAttachables(chanDescriptor).unwrap()
      state.value = ReplyLayoutFilesState(newAttachFiles)
    }
  }

  private suspend fun enumerateReplyAttachables(
    chanDescriptor: ChanDescriptor
  ): ModularResult<MutableList<IReplyAttachable>> {
    return withContext(Dispatchers.IO) {
      return@withContext Try {
        val newAttachFiles = mutableListOf<IReplyAttachable>()
        val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
        val fileFileSizeMap = mutableMapOf<String, Long>()

        var attachableCounter = 0

        val replyFiles = replyManager.mapOrderedNotNull { _, replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "getReplyFileMeta() error", error)
            return@mapOrderedNotNull null
          }

          if (replyFileMeta.isTaken()) {
            return@mapOrderedNotNull null
          }

          ++attachableCounter

          if (attachableCounter > MAX_VISIBLE_ATTACHABLES_COUNT) {
            return@mapOrderedNotNull null
          }

          fileFileSizeMap[replyFile.fileOnDisk.absolutePath] = replyFile.fileOnDisk.length()
          return@mapOrderedNotNull replyFile
        }

        if (replyFiles.isNotEmpty()) {
          val maxAllowedTotalFilesSizePerPost = getTotalFileSizeSumPerPost(chanDescriptor)
            ?: -1
          val totalFileSizeSum = fileFileSizeMap.values.sum()
          val boardSupportsSpoilers = boardSupportsSpoilers()

          val replyFileAttachables = replyFiles.map { replyFile ->
            val replyFileMeta = replyFile.getReplyFileMeta().unwrap()

            val isSelected = replyManager.isSelected(replyFileMeta.fileUuid).unwrap()
            val selectedFilesCount = replyManager.selectedFilesCount().unwrap()
            val fileExifStatus = getFileExifInfoStatus(replyFile.fileOnDisk)
            val exceedsMaxFileSize =
              fileExceedsMaxFileSize(replyFile, replyFileMeta, chanDescriptor)
            val markedAsSpoilerOnNonSpoilerBoard =
              isSelected && replyFileMeta.spoiler && !boardSupportsSpoilers

            val totalFileSizeExceeded = if (maxAllowedTotalFilesSizePerPost <= 0) {
              false
            } else {
              totalFileSizeSum > maxAllowedTotalFilesSizePerPost
            }

            val spoilerInfo = if (!boardSupportsSpoilers && !replyFileMeta.spoiler) {
              null
            } else {
              EpoxyReplyFileView.SpoilerInfo(replyFileMeta.spoiler, boardSupportsSpoilers)
            }

            val imageDimensions = MediaUtils.getImageDims(replyFile.fileOnDisk)
              ?.let { dimensions -> ReplyFileAttachable.ImageDimensions(dimensions.first!!, dimensions.second!!) }

            return@map ReplyFileAttachable(
              fileUuid = replyFileMeta.fileUuid,
              fileName = replyFileMeta.fileName,
              spoilerInfo = spoilerInfo,
              selected = isSelected,
              fileSize = fileFileSizeMap[replyFile.fileOnDisk.absolutePath]!!,
              imageDimensions = imageDimensions,
              attachAdditionalInfo = EpoxyReplyFileView.AttachAdditionalInfo(
                fileExifStatus = fileExifStatus,
                totalFileSizeExceeded = totalFileSizeExceeded,
                fileMaxSizeExceeded = exceedsMaxFileSize,
                markedAsSpoilerOnNonSpoilerBoard = markedAsSpoilerOnNonSpoilerBoard
              ),
              maxAttachedFilesCountExceeded = when {
                maxAllowedFilesPerPost == null -> false
                selectedFilesCount < maxAllowedFilesPerPost -> false
                selectedFilesCount == maxAllowedFilesPerPost -> !isSelected
                else -> true
              }
            )
          }

          newAttachFiles.addAll(replyFileAttachables)
        }

        if (attachableCounter != MAX_VISIBLE_ATTACHABLES_COUNT) {
          if (attachableCounter > MAX_VISIBLE_ATTACHABLES_COUNT) {
            newAttachFiles.add(TooManyAttachables(attachableCounter))
          } else {
            newAttachFiles.add(ReplyNewAttachable())
          }
        }

        return@Try newAttachFiles
      }
    }
  }

  private fun fileExceedsMaxFileSize(
    replyFile: ReplyFile,
    replyFileMeta: ReplyFileMeta,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    val chanBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return false

    val isProbablyVideo = imageLoaderV2.replyFileIsProbablyVideo(replyFile, replyFileMeta)
    val fileOnDiskSize = replyFile.fileOnDisk.length()

    if (chanBoard.maxWebmSize > 0 && isProbablyVideo) {
      return fileOnDiskSize > chanBoard.maxWebmSize
    }

    if (chanBoard.maxFileSize > 0 && !isProbablyVideo) {
      return fileOnDiskSize > chanBoard.maxFileSize
    }

    return false
  }

  private fun getFileExifInfoStatus(fileOnDisk: File): Set<FileExifInfoStatus> {
    try {
      val resultSet = hashSetOf<FileExifInfoStatus>()
      val exif = ExifInterface(fileOnDisk.absolutePath)

      val orientation =
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

      if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
        val orientationString = when (orientation) {
          ExifInterface.ORIENTATION_NORMAL -> "orientation_normal"
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "orientation_flip_horizontal"
          ExifInterface.ORIENTATION_ROTATE_180 -> "orientation_rotate_180"
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> "orientation_flip_vertical"
          ExifInterface.ORIENTATION_TRANSPOSE -> "orientation_transpose"
          ExifInterface.ORIENTATION_ROTATE_90 -> "orientation_rotate_90"
          ExifInterface.ORIENTATION_TRANSVERSE -> "orientation_transverse"
          ExifInterface.ORIENTATION_ROTATE_270 -> "orientation_rotate_270"
          else -> "orientation_undefined"
        }

        resultSet += FileExifInfoStatus.OrientationExifFound(orientationString)
      }

      if (exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null
        || exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null) {
        val fullString = buildString {
          append("GPS_LONGITUDE='${exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)}', ")
          append("GPS_LATITUDE='${exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)}'")
        }

        resultSet += FileExifInfoStatus.GpsExifFound(fullString)
      }

      return resultSet
    } catch (ignored: Exception) {
      return emptySet()
    }
  }

  private fun canAutoSelectFile(maxAllowedFilesPerPost: Int): ModularResult<Boolean> {
    return Try { replyManager.selectedFilesCount().unwrap() < maxAllowedFilesPerPost }
  }

  private suspend fun getMaxAllowedFilesPerPost(chanDescriptor: ChanDescriptor): Int? {
    return postingLimitationsInfoManager.getMaxAllowedFilesPerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  private suspend fun getTotalFileSizeSumPerPost(chanDescriptor: ChanDescriptor): Long? {
    return postingLimitationsInfoManager.getMaxAllowedTotalFilesSizePerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  private suspend inline fun handleStateUpdate(updater: () -> Unit) {
    try {
      updater()
    } catch (error: Throwable) {
      if (error is CancellationException) {
        return
      }

      Logger.e(TAG, "handleStateUpdate() error", error)
      withView { showGenericErrorToast(error.errorMessageOrClassName()) }
    }
  }

  fun onFileStatusRequested(fileUuid: UUID) {
    scope.launch {
      val chanDescriptor = boundChanDescriptor
        ?: return@launch

      val clickedFile = state.value.attachables.firstOrNull { replyAttachable ->
        replyAttachable is ReplyFileAttachable && replyAttachable.fileUuid == fileUuid
      } as? ReplyFileAttachable

      if (clickedFile == null) {
        return@launch
      }

      val chanBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())

      val maxBoardFileSizeRaw = chanBoard?.maxFileSize ?: -1
      val maxBoardFileSize = chanBoard?.maxFileSize
        ?.takeIf { maxFileSize -> maxFileSize > 0 }
        ?.let { maxFileSize -> getReadableFileSize(maxFileSize.toLong()) }
        ?: "???"

      val maxBoardWebmSizeRaw = chanBoard?.maxWebmSize ?: -1
      val maxBoardWebmSize = chanBoard?.maxWebmSize
        ?.takeIf { maxWebmSize -> maxWebmSize > 0 }
        ?.let { maxWebmSize -> getReadableFileSize(maxWebmSize.toLong()) }
        ?: "???"

      val totalFileSizeSum = getTotalFileSizeSumPerPost(chanDescriptor)
      val attachAdditionalInfo = clickedFile.attachAdditionalInfo

      val fileStatusString = buildString {
        appendLine("File name: \"${clickedFile.fileName}\"")

        clickedFile.spoilerInfo?.let { spoilerInfo ->
          appendLine("Marked as spoiler: ${spoilerInfo.markedAsSpoiler}, " +
            "(Board supports spoilers: ${spoilerInfo.boardSupportsSpoilers})")
        }

        appendLine("File size: ${getReadableFileSize(clickedFile.fileSize)}")

        clickedFile.imageDimensions?.let { imageDimensions ->
          appendLine("Image dimensions: ${imageDimensions.width}x${imageDimensions.height}")
        }

        if (maxBoardFileSizeRaw > 0 && clickedFile.fileSize > maxBoardFileSizeRaw) {
          appendLine("Exceeds max board file size: true, board max file size: $maxBoardFileSize")
        }

        if (maxBoardWebmSizeRaw > 0 && clickedFile.fileSize > maxBoardWebmSizeRaw) {
          appendLine("Exceeds max board webm size: true, Board max webm size: $maxBoardWebmSize")
        }

        if (totalFileSizeSum != null && totalFileSizeSum > 0) {
          val totalFileSizeSumFormatted = getReadableFileSize(totalFileSizeSum)

          appendLine(
            "Total file size sum per post: ${totalFileSizeSumFormatted}, " +
              "exceeded: ${attachAdditionalInfo.totalFileSizeExceeded}"
          )
        }

        val gpsExifData = attachAdditionalInfo.getGspExifDataOrNull()
        if (gpsExifData != null) {
          appendLine("GPS exif data='${gpsExifData.value}'")
        }

        val orientationExifData = attachAdditionalInfo.getOrientationExifData()
        if (orientationExifData != null) {
          appendLine("Orientation exif data='${orientationExifData.value}'")
        }
      }

      withView { showFileStatusMessage(fileStatusString) }
    }
  }

  fun isFileSupportedForReencoding(clickedFileUuid: UUID): Boolean {
    val replyFile = replyManager.getReplyFileByFileUuid(clickedFileUuid).valueOrNull()
      ?: return false

    return MediaUtils.isFileSupportedForReencoding(replyFile.fileOnDisk)
  }

  sealed class FileExifInfoStatus {
    data class GpsExifFound(val value: String) : FileExifInfoStatus()
    data class OrientationExifFound(val value: String) : FileExifInfoStatus()
  }

  companion object {
    private const val TAG = "ReplyLayoutFilesAreaPresenter"
    const val MAX_VISIBLE_ATTACHABLES_COUNT = 32

    private const val REFRESH_FILES_DEBOUNCE_TIME = 250L
    private const val FILE_SELECTION_UPDATE_DEBOUNCE_TIME = 25L
    private const val REFRESH_FILES_APPROX_DURATION = 125L
  }

}