package com.github.k1rakishou.chan.features.reply

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.IReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyNewAttachable
import com.github.k1rakishou.chan.features.reply.data.TooManyAttachables
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ReplyLayoutFilesAreaPresenter(
  private val appConstants: AppConstants,
  private val replyManager: ReplyManager,
  private val postingLimitationsInfoManager: PostingLimitationsInfoManager,
  private val imagePickHelper: ImagePickHelper
) : BasePresenter<ReplyLayoutFilesAreaView>() {
  private val pickFilesExecutor = RendezvousCoroutineExecutor(scope)
  private val refreshFilesExecutor = DebouncingCoroutineExecutor(scope)
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
          showLoadingView = { withView { showLoadingView(cancellationFunc, R.string.decoding_reply_file_preview) } },
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
          Logger.e(TAG, "pickNewLocalFile() error, " +
            "pickedFileResult=${pickedFileResult.reason.errorMessageOrClassName()}"
          )

          withView { showFilePickerErrorToast(pickedFileResult.reason) }
          return@handleStateUpdate
        }

        val replyFile = (pickedFileResult as PickedFile.Result).replyFiles.first()

        val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(TAG, "imagePickHelper.pickLocalFile($chanDescriptor) getReplyFileMeta() error", error)
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
          Logger.e(TAG, "pickRemoteFile() error, " +
            "pickedFileResult=${pickedFileResult.reason.errorMessageOrClassName()}"
          )

          withView { showFilePickerErrorToast(pickedFileResult.reason) }
          return@handleStateUpdate
        }

        val replyFile = (pickedFileResult as PickedFile.Result).replyFiles.first()

        val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(TAG, "imagePickHelper.pickRemoteFile($chanDescriptor) getReplyFileMeta() error", error)
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

  fun refreshAttachedFiles() {
    refreshFilesExecutor.post(REFRESH_FILES_DEBOUNCE_TIME) {
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
    return Try {
      val newAttachFiles = mutableListOf<IReplyAttachable>()
      val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
      var attachableCounter = 0

      val replyFileAttachables = replyManager.mapOrderedNotNull { _, replyFile ->
        val meta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(TAG, "getReplyFileMeta() error", error)
          return@mapOrderedNotNull null
        }

        if (meta.isTaken()) {
          return@mapOrderedNotNull null
        }

        ++attachableCounter

        if (attachableCounter > MAX_ATTACHABLES_COUNT) {
          return@mapOrderedNotNull null
        }

        val isSelected = replyManager.isSelected(meta.fileUuid).unwrap()
        val selectedFilesCount = replyManager.selectedFilesCount().unwrap()

        return@mapOrderedNotNull ReplyFileAttachable(
          fileUuid = meta.fileUuid,
          fileName = meta.fileName,
          spoiler = meta.spoiler,
          selected = isSelected,
          exceedsMaxFilesLimit = when {
            maxAllowedFilesPerPost == null -> false
            selectedFilesCount < maxAllowedFilesPerPost -> false
            selectedFilesCount == maxAllowedFilesPerPost -> !isSelected
            else -> true
          }
        )
      }

      newAttachFiles.addAll(replyFileAttachables)

      if (attachableCounter != MAX_ATTACHABLES_COUNT) {
        if (attachableCounter > MAX_ATTACHABLES_COUNT) {
          newAttachFiles.add(TooManyAttachables(attachableCounter))
        } else {
          newAttachFiles.add(ReplyNewAttachable())
        }
      }

      return@Try newAttachFiles
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

  private suspend inline fun handleStateUpdate(updater: () -> Unit) {
    try {
      updater()
    } catch (error: Throwable) {
      Logger.e(TAG, "handleStateUpdate() error", error)
      withView { showGenericErrorToast(error.errorMessageOrClassName()) }
      state.value = ReplyLayoutFilesState(listOf(ReplyNewAttachable()))
    }
  }

  companion object {
    private const val TAG = "ReplyLayoutFilesAreaPresenter"
    const val MAX_ATTACHABLES_COUNT = 32

    private const val REFRESH_FILES_DEBOUNCE_TIME = 250L
    private const val REFRESH_FILES_APPROX_DURATION = 125L
  }

}