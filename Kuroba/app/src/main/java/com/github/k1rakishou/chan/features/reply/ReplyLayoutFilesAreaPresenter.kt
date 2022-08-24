package com.github.k1rakishou.chan.features.reply

import android.Manifest
import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.AttachAdditionalInfo
import com.github.k1rakishou.chan.features.reply.data.IReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.features.reply.data.ReplyNewAttachable
import com.github.k1rakishou.chan.features.reply.data.SpoilerInfo
import com.github.k1rakishou.chan.features.reply.data.TooManyAttachables
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.HashingUtil
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.ModularResult.Companion.value
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class ReplyLayoutFilesAreaPresenter(
  private val context: Context,
  private val appConstants: AppConstants,
  private val replyManager: Lazy<ReplyManager>,
  private val boardManager: Lazy<BoardManager>,
  private val imageLoaderV2: Lazy<ImageLoaderV2>,
  private val postingLimitationsInfoManager: Lazy<PostingLimitationsInfoManager>,
  private val imagePickHelper: Lazy<ImagePickHelper>,
  private val runtimePermissionsHelper: RuntimePermissionsHelper
) : BasePresenter<ReplyLayoutFilesAreaView>() {
  private val pickFilesExecutor = RendezvousCoroutineExecutor(scope)
  private val refreshFilesExecutor = DebouncingCoroutineExecutor(scope)
  private val fileChangeExecutor = SerializedCoroutineExecutor(scope)
  private val state = MutableStateFlow(ReplyLayoutFilesState(loading = true))

  private var _boundChanDescriptor: ChanDescriptor? = null
  val boundChanDescriptor: ChanDescriptor?
    get() = _boundChanDescriptor

  fun listenForStateUpdates(): Flow<ReplyLayoutFilesState> = state.asStateFlow()

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      reloadFilesFromDiskAndInitState(chanDescriptor)
      return
    }

    this._boundChanDescriptor = chanDescriptor

    scope.launch {
      imagePickHelper.get().listenForNewPickedFiles()
        .collect { refreshAttachedFiles() }
    }

    scope.launch {
      replyManager.get().listenForReplyFilesUpdates()
        .collect { refreshAttachedFiles() }
    }

    reloadFilesFromDiskAndInitState(chanDescriptor)
  }

  fun unbindChanDescriptor() {
    this._boundChanDescriptor = null
  }

  fun pickLocalFile(showFilePickerChooser: Boolean) {
    if (AppModuleAndroidUtils.checkDontKeepActivitiesSettingEnabledForWarningDialog(context)) {
      withViewNormal { onDontKeepActivitiesSettingDetected() }
      return
    }

    pickFilesExecutor.post {
      handleStateUpdate {
        val chanDescriptor = _boundChanDescriptor
          ?: return@handleStateUpdate

        val granted = requestPermissionIfNeededSuspend()
        if (!granted) {
          withView { showGenericErrorToast("Permission was not granted") }
          return@handleStateUpdate
        }

        val job = SupervisorJob()
        val cancellationFunc = { job.cancel() }

        val input = LocalFilePicker.LocalFilePickerInput(
          notifyListeners = false,
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

        val pickedFileResult = withContext(job) { imagePickHelper.get().pickLocalFile(input) }
          .finally { withView { hideLoadingView() } }
          .safeUnwrap { error ->
            Logger.e(TAG, "imagePickHelper.pickLocalFile($chanDescriptor) error", error)
            withView { showGenericErrorToast(error.errorMessageOrClassName()) }
            return@handleStateUpdate
          }

        if (pickedFileResult is PickedFile.Failure) {
          Logger.e(TAG, "pickNewLocalFile() error, " +
              "pickedFileResult=${pickedFileResult.reason.errorMessageOrClassName()}")

          withView { showFilePickerErrorToast(pickedFileResult.reason) }
          return@handleStateUpdate
        }

        val replyFiles = (pickedFileResult as PickedFile.Result).replyFiles
        replyFiles.forEach { replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "imagePickHelper.pickLocalFile($chanDescriptor) getReplyFileMeta() error", error)
            return@forEach
          }

          val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
          if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
            replyManager.get().updateFileSelection(
              fileUuid = replyFileMeta.fileUuid,
              selected = true,
              notifyListeners = false
            )
          }
        }

        Logger.d(TAG, "pickNewLocalFile() success")
        refreshAttachedFiles()
      }
    }
  }

  fun pickRemoteFile(url: String) {
    pickOneOfRemoteFiles(listOf(url))
  }

  fun pickOneOfRemoteFiles(urls: List<String>) {
    if (urls.isEmpty()) {
      return
    }

    if (AppModuleAndroidUtils.checkDontKeepActivitiesSettingEnabledForWarningDialog(context)) {
      withViewNormal { onDontKeepActivitiesSettingDetected() }
      return
    }

    pickFilesExecutor.post {
      handleStateUpdate {
        val chanDescriptor = _boundChanDescriptor
          ?: return@handleStateUpdate

        val job = SupervisorJob()
        val cancellationFunc = { job.cancel() }

        val input = RemoteFilePicker.RemoteFilePickerInput(
          notifyListeners = false,
          replyChanDescriptor = chanDescriptor,
          imageUrls = urls,
          showLoadingView = { textId -> withView { showLoadingView(cancellationFunc, textId) } },
          hideLoadingView = { withView { hideLoadingView() } }
        )

        val pickedFileResult = withContext(job) { imagePickHelper.get().pickRemoteFile(input) }
          .finally { withView { hideLoadingView() } }
          .safeUnwrap { error ->
            Logger.e(TAG, "imagePickHelper.pickRemoteFile($chanDescriptor) error", error)
            withView { showGenericErrorToast(error.errorMessageOrClassName()) }
            return@handleStateUpdate
          }

        if (pickedFileResult is PickedFile.Failure) {
          Logger.e(TAG,
            "pickRemoteFile() error, pickedFileResult=${pickedFileResult.reason.errorMessageOrClassName()}"
          )

          withView { showFilePickerErrorToast(pickedFileResult.reason) }
          return@handleStateUpdate
        }

        val replyFiles = (pickedFileResult as PickedFile.Result).replyFiles
        replyFiles.forEach { replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "imagePickHelper.pickRemoteFile($chanDescriptor) getReplyFileMeta() error", error)
            return@forEach
          }

          val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
          if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
            replyManager.get().updateFileSelection(
              fileUuid = replyFileMeta.fileUuid,
              selected = true,
              notifyListeners = false
            )
          }
        }

        Logger.d(TAG, "pickRemoteFile() success")
        refreshAttachedFiles()
      }
    }
  }

  fun hasSelectedFiles(): Boolean = replyManager.get().hasSelectedFiles().unwrap()

  fun allFilesSelected(): Boolean {
    val totalFilesCount = Math.min(MAX_VISIBLE_ATTACHABLES_COUNT, replyManager.get().totalFilesCount().unwrap())
    val selectedFilesCount = replyManager.get().selectedFilesCount().unwrap()

    return totalFilesCount == selectedFilesCount
  }

  fun totalFilesCount(): Int = replyManager.get().totalFilesCount().unwrap()

  fun selectedFilesCount(): Int = replyManager.get().selectedFilesCount().unwrap()

  private fun boardSupportsSpoilers(): Boolean {
    val chanDescriptor = _boundChanDescriptor
      ?: return false

    return boardManager.get().byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?.spoilers
      ?: return false
  }

  fun updateFileSelection(fileUuid: UUID) {
    fileChangeExecutor.post {
      handleStateUpdate {
        val nowSelected = replyManager.get().isSelected(fileUuid).unwrap().not()

        replyManager.get().updateFileSelection(
          fileUuid = fileUuid,
          selected = nowSelected,
          notifyListeners = false
        )
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
        val nowMarkedAsSpoiler = replyManager.get().isMarkedAsSpoiler(fileUuid).unwrap().not()

        replyManager.get().updateFileSpoilerFlag(
          fileUuid = fileUuid,
          spoiler = nowMarkedAsSpoiler,
          notifyListeners = false
        )
          .safeUnwrap { error ->
            Logger.e(TAG, "updateFileSpoilerFlag($fileUuid, $nowMarkedAsSpoiler) error", error)
            return@handleStateUpdate
          }

        refreshAttachedFiles()
      }
    }
  }

  fun deleteFile(fileUuid: UUID) {
    fileChangeExecutor.post {
      handleStateUpdate {
        replyManager.get().deleteFile(fileUuid = fileUuid, notifyListeners = false)
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
        replyManager.get().deleteSelectedFiles(notifyListeners = false)
          .safeUnwrap { error ->
            Logger.e(TAG, "deleteSelectedFiles() error", error)
            return@handleStateUpdate
          }

        refreshAttachedFiles()
      }
    }
  }

  fun removeSelectedFilesName() {
    fileChangeExecutor.post {
      handleStateUpdate {
        withContext(Dispatchers.Default) {
          replyManager.get().iterateSelectedFilesOrdered { _, replyFile, replyFileMeta ->
            val newFileName = replyManager.get().getNewImageName(replyFileMeta.fileName)

            replyFile.updateFileName(newFileName)
              .peekError { error -> Logger.e(TAG, "Failed to update file name", error) }
              .ignore()
          }
        }

        refreshAttachedFiles()
      }
    }
  }

  fun removeSelectedFilesMetadata(context: Context) {
    fileChangeExecutor.post {
      handleStateUpdate {
        withView {
          showLoadingView(
            titleTextId = R.string.layout_reply_files_area_removing_metadata,
            cancellationFunc = {}
          )
        }

        withContext(Dispatchers.Default) {
          val selectedReplyFiles = replyManager.get().getSelectedFilesOrdered()

          selectedReplyFiles.forEach { replyFile ->
            val replyFileMeta = replyFile.getReplyFileMeta().valueOrNull()
              ?: return@forEach

            val reencodedFile = MediaUtils.reencodeBitmapFile(
              inputBitmapFile = replyFile.fileOnDisk,
              fixExif = false,
              removeMetadata = true,
              changeImageChecksum = false,
              reencodeSettings = null
            )

            if (reencodedFile == null) {
              Logger.e(TAG, "removeSelectedFilesMetadata() Failed to remove metadata for " +
                "file '${replyFile.fileOnDisk.absolutePath}'")
              return@forEach
            }

            val isSuccess = replyFile.overwriteFileOnDisk(reencodedFile)
              .peekError { error ->
                Logger.e(TAG, "removeSelectedFilesMetadata() Failed to overwrite " +
                  "file '${replyFile.fileOnDisk.absolutePath}' " +
                  "with '${reencodedFile.absolutePath}'", error)
              }
              .isValue()

            if (!isSuccess) {
              return@forEach
            }

            imageLoaderV2.get().calculateFilePreviewAndStoreOnDisk(
              context,
              replyFileMeta.fileUuid
            )
          }
        }

        withView { hideLoadingView() }

        refreshAttachedFiles()
      }
    }
  }

  fun changeSelectedFilesChecksum(context: Context) {
    fileChangeExecutor.post {
      handleStateUpdate {
        withView {
          showLoadingView(
            titleTextId = R.string.layout_reply_files_area_changing_checksum,
            cancellationFunc = {}
          )
        }

        withContext(Dispatchers.Default) {
          val selectedReplyFiles = replyManager.get().getSelectedFilesOrdered()

          selectedReplyFiles.forEach { replyFile ->
            val replyFileMeta = replyFile.getReplyFileMeta().valueOrNull()
              ?: return@forEach

            val reencodedFile = MediaUtils.reencodeBitmapFile(
              inputBitmapFile = replyFile.fileOnDisk,
              fixExif = false,
              removeMetadata = false,
              changeImageChecksum = true,
              reencodeSettings = null
            )

            if (reencodedFile == null) {
              Logger.e(TAG, "changeSelectedFilesChecksum() Failed to change checksum for " +
                "file '${replyFile.fileOnDisk.absolutePath}'")
              return@forEach
            }

            val isSuccess = replyFile.overwriteFileOnDisk(reencodedFile)
              .peekError { error ->
                Logger.e(TAG, "changeSelectedFilesChecksum() Failed to overwrite " +
                  "file '${replyFile.fileOnDisk.absolutePath}' " +
                  "with '${reencodedFile.absolutePath}'", error)
              }
              .isValue()

            if (!isSuccess) {
              return@forEach
            }

            imageLoaderV2.get().calculateFilePreviewAndStoreOnDisk(
              context,
              replyFileMeta.fileUuid
            )
          }
        }

        withView { hideLoadingView() }

        refreshAttachedFiles()
      }
    }
  }

  fun selectUnselectAll(selectAll: Boolean) {
    fileChangeExecutor.post {
      handleStateUpdate {
        withView {
          showLoadingView(
            titleTextId = R.string.layout_reply_files_area_updating_selection,
            cancellationFunc = {}
          )
        }

        val needRefresh = withContext(Dispatchers.Default) {
          val toUpdate = mutableListOf<UUID>()

          replyManager.get().iterateFilesOrdered { _, _, replyFileMeta ->
            if (replyFileMeta.selected != selectAll) {
              toUpdate += replyFileMeta.fileUuid
            }
          }

          if (toUpdate.isEmpty()) {
            return@withContext false
          }

          toUpdate.forEach { fileUuid ->
            replyManager.get().updateFileSelection(
              fileUuid = fileUuid,
              selected = selectAll,
              notifyListeners = false
            )
          }

          return@withContext true
        }

        withView { hideLoadingView() }

        if (needRefresh) {
          refreshAttachedFiles()
        }
      }
    }
  }

  fun hasAttachedFiles(): Boolean {
    return state.value.attachables.any { replyAttachable -> replyAttachable is ReplyFileAttachable }
  }

  fun refreshAttachedFiles(
    isReplyLayoutExpanded: Boolean = state.value.isReplyLayoutExpanded,
    debounceTime: Long = REFRESH_FILES_DEBOUNCE_TIME
  ) {
    refreshFilesExecutor.post(debounceTime) {
      handleStateUpdate {
        val chanDescriptor = _boundChanDescriptor
          ?: return@handleStateUpdate

        val attachables = enumerateReplyAttachables(chanDescriptor).unwrap()

        val oldState = state.value
        val newState = ReplyLayoutFilesState(
          isReplyLayoutExpanded = isReplyLayoutExpanded,
          loading = false,
          attachables = attachables
        )
        state.value = newState

        if (oldState != newState) {
          withView {
            val selectedFilesCount = state.value.attachables.count { replyAttachable ->
              replyAttachable is ReplyFileAttachable && replyAttachable.selected
            }
            val totalFilesCount = state.value.attachables.count { replyAttachable ->
              replyAttachable is ReplyFileAttachable
            }
            
            val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
            if (maxAllowedFilesPerPost != null) {
              updateSelectedFilesCounter(selectedFilesCount, maxAllowedFilesPerPost, totalFilesCount)
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

  private suspend fun requestPermissionIfNeededSuspend(): Boolean {
    val permission = Manifest.permission.READ_EXTERNAL_STORAGE

    if (runtimePermissionsHelper.hasPermission(permission)) {
      return true
    }

    return suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
      runtimePermissionsHelper.requestPermission(permission) { granted ->
        cancellableContinuation.resumeValueSafe(granted)
      }
    }
  }

  private suspend fun reloadFilesFromDiskAndInitState(chanDescriptor: ChanDescriptor) {
    handleStateUpdate {
      withContext(Dispatchers.IO) { replyManager.get().reloadFilesFromDisk(appConstants) }
        .unwrap()

      replyManager.get().iterateFilesOrdered { _, _, replyFileMeta ->
        if (replyFileMeta.selected) {
          replyManager.get().updateFileSelection(
            fileUuid = replyFileMeta.fileUuid,
            selected = true,
            notifyListeners = false
          )
        }
      }

      val newAttachFiles = enumerateReplyAttachables(chanDescriptor).unwrap()
      state.value = ReplyLayoutFilesState(attachables = newAttachFiles)
    }
  }

  private suspend fun enumerateReplyAttachables(
    chanDescriptor: ChanDescriptor
  ): ModularResult<List<IReplyAttachable>> {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return value(listOf())
    }

    return withContext(Dispatchers.IO) {
      return@withContext Try {
        val newAttachFiles = mutableListOf<IReplyAttachable>()
        val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
        val fileFileSizeMap = mutableMapOf<String, Long>()

        var attachableCounter = 0

        val replyFiles = replyManager.get().mapOrderedNotNull { _, replyFile ->
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

            val isSelected = replyManager.get().isSelected(replyFileMeta.fileUuid).unwrap()
            val selectedFilesCount = replyManager.get().selectedFilesCount().unwrap()
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
              SpoilerInfo(replyFileMeta.spoiler, boardSupportsSpoilers)
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
              attachAdditionalInfo = AttachAdditionalInfo(
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

  private suspend fun fileExceedsMaxFileSize(
    replyFile: ReplyFile,
    replyFileMeta: ReplyFileMeta,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return false
    }

    val chanBoard = boardManager.get().byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return false

    val isProbablyVideo = imageLoaderV2.get().fileIsProbablyVideoInterruptible(
      replyFileMeta.originalFileName,
      InputFile.JavaFile(replyFile.fileOnDisk)
    )

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
    return Try { replyManager.get().selectedFilesCount().unwrap() < maxAllowedFilesPerPost }
  }

  private suspend fun getMaxAllowedFilesPerPost(chanDescriptor: ChanDescriptor): Int? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    return postingLimitationsInfoManager.get().getMaxAllowedFilesPerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  private suspend fun getTotalFileSizeSumPerPost(chanDescriptor: ChanDescriptor): Long? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    return postingLimitationsInfoManager.get().getMaxAllowedTotalFilesSizePerPost(
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
      val chanDescriptor = _boundChanDescriptor
        ?: return@launch

      val clickedFile = state.value.attachables.firstOrNull { replyAttachable ->
        replyAttachable is ReplyFileAttachable && replyAttachable.fileUuid == fileUuid
      } as? ReplyFileAttachable

      if (clickedFile == null) {
        return@launch
      }

      val chanBoard = boardManager.get().byBoardDescriptor(chanDescriptor.boardDescriptor())

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

      val fileMd5Hash = withContext(Dispatchers.Default) {
        val replyFile = replyManager.get().getReplyFileByFileUuid(fileUuid).valueOrNull()
          ?: return@withContext null

        return@withContext HashingUtil.fileHash(replyFile.fileOnDisk)
      }

      val fileStatusString = buildString {
        appendLine("File name: \"${clickedFile.fileName}\"")

        if (fileMd5Hash != null) {
          appendLine("File MD5 hash: \"${fileMd5Hash}\"")
        }

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
    val replyFile = replyManager.get().getReplyFileByFileUuid(clickedFileUuid).valueOrNull()
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