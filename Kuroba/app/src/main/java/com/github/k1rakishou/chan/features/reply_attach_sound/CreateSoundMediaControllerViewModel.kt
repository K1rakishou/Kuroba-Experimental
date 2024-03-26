package com.github.k1rakishou.chan.features.reply_attach_sound

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.loader.CanceledByUserException
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.usecase.UploadFileToCatBoxUseCase
import com.github.k1rakishou.chan.ui.helper.FileHelper
import com.github.k1rakishou.chan.utils.openChooseFileDialogAsync
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.rethrowCancellationException
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.net.URLEncoder
import java.util.*
import javax.inject.Inject

class CreateSoundMediaControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val replyManager: ReplyManager,
  private val fileHelper: FileHelper,
  private val uploadFileToCatBoxUseCase: UploadFileToCatBoxUseCase,
) : BaseViewModel() {

  private val _activeRequests = mutableMapOf<UUID, Job>()

  private val _selectedFiles = mutableStateMapOf<UUID, Uri>()
  val selectedFiles: Map<UUID, Uri>
    get() = _selectedFiles

  private val _attachments = mutableStateListOf<Attachment>()
  val attachments: List<Attachment>
    get() = _attachments

  private val _processingAttachments = mutableStateMapOf<UUID, AsyncData<Unit>>()
  val processingAttachments: Map<UUID, AsyncData<Unit>>
    get() = _processingAttachments

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    viewModelScope.launch(Dispatchers.IO) {
      refreshAttachedFiles()
    }

    viewModelScope.launch {
      replyManager.listenForReplyFilesUpdates()
        .onEach { refreshAttachedFiles() }
        .flowOn(Dispatchers.IO)
        .collect()
    }
  }

  fun cancelCreatingSoundMedia(clickedAttachment: Attachment) {
    Logger.debug(TAG) {
      "cancelCreatingSoundMedia() cancelling sound media creation for file with UUID '${clickedAttachment.fileUUID}'"
    }

    _selectedFiles.remove(clickedAttachment.fileUUID)
    _activeRequests.remove(clickedAttachment.fileUUID)?.cancel()
    _processingAttachments[clickedAttachment.fileUUID] = AsyncData.NotInitialized
  }

  fun checkMediaAlreadyHasSoundAttached(
    clickedAttachment: Attachment
  ): Boolean {
    val matcher = AppConstants.SOUND_POST_PATTERN.matcher(clickedAttachment.attachmentName)
    return matcher.find()
  }

  fun tryToCreateSoundMedia(
    fileChooser: FileChooser,
    clickedAttachment: Attachment,
    showErrorToast: (Throwable) -> Unit,
    showSuccessToast: (String) -> Unit
  ) {
    // TODO: show 'open sound file' dialog on first use
    // TODO: consider allowing attaching videos after we start supporting sound posts with videos

    val job = viewModelScope.launch {
      _processingAttachments[clickedAttachment.fileUUID] = AsyncData.Loading

      try {
        val pickedFileUri = askUserToPickSoundFile(fileChooser, clickedAttachment)
        if (pickedFileUri == null) {
          Logger.debug(TAG) { "tryToCreateSoundMedia() pickedFileUri == null" }

          showErrorToast(CanceledByUserException())
          _processingAttachments.remove(clickedAttachment.fileUUID)
          return@launch
        }

        val fileMimeType = fileHelper.getFileMimeType(pickedFileUri).valueOrNull()
        if (fileMimeType == null || !fileMimeType.isAudio()) {
          Logger.debug(TAG) { "tryToCreateSoundMedia() File '${pickedFileUri}' is not an Audio file" }

          throw ClientException("File '${pickedFileUri}' is not an Audio file")
        }

        val fileName = fileHelper.getFileName(pickedFileUri).valueOrNull()
        if (fileName == null || fileName.isNullOrBlank()) {
          Logger.debug(TAG) { "tryToCreateSoundMedia() File '${pickedFileUri}' has no name" }

          throw ClientException("File '${pickedFileUri}' has no name")
        }

        val extension = fileName.substringAfterLast('.')
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mimeType == null || mimeType.isNullOrBlank()) {
          Logger.debug(TAG) { "tryToCreateSoundMedia() File '${pickedFileUri}' has no extension" }

          throw ClientException("File '${pickedFileUri}' has no extension")
        }

        _selectedFiles[clickedAttachment.fileUUID] = pickedFileUri

        val uploadResult = uploadFileToCatBoxUseCase.await(
          fileUri = pickedFileUri,
          mimeType = fileMimeType.mimeType,
          extension = extension
        )

        val uploadedFileUrl = when (uploadResult) {
          is ModularResult.Error -> throw uploadResult.error
          is ModularResult.Value -> uploadResult.value
        }

        val oldAttachmentName = clickedAttachment.attachmentName

        withContext(Dispatchers.IO) {
          injectSoundUrlIntoFileName(
            clickedAttachment = clickedAttachment,
            uploadedFileUrl = uploadedFileUrl
          )
        }

        _selectedFiles.remove(clickedAttachment.fileUUID)
        _processingAttachments[clickedAttachment.fileUUID] = AsyncData.Data(Unit)
        showSuccessToast(oldAttachmentName)

        Logger.debug(TAG) {
          "tryToCreateSoundMedia() successfully created sound media " +
            "for file with UUID '${clickedAttachment.fileUUID}'"
        }
      } catch (error: Throwable) {
        error.rethrowCancellationException()

        Logger.error(TAG) { "tryToCreateSoundMedia() error: ${error.errorMessageOrClassName()}" }
        _processingAttachments[clickedAttachment.fileUUID] = AsyncData.Error(error)
        showErrorToast(error)
      }
    }

    _activeRequests[clickedAttachment.fileUUID]?.cancel()
    _activeRequests[clickedAttachment.fileUUID] = job
  }

  private suspend fun askUserToPickSoundFile(
    fileChooser: FileChooser,
    clickedAttachment: Attachment
  ): Uri? {
    val previouslyPickedFileUri = _selectedFiles[clickedAttachment.fileUUID]
    if (previouslyPickedFileUri != null) {
      return previouslyPickedFileUri
    }

    val pickedFileUri = when (val openFileResult = fileChooser.openChooseFileDialogAsync()) {
      is ModularResult.Error -> throw openFileResult.error
      is ModularResult.Value -> openFileResult.value
    }

    if (pickedFileUri == null) {
      return null
    }

    return pickedFileUri
  }

  private fun injectSoundUrlIntoFileName(
    clickedAttachment: Attachment,
    uploadedFileUrl: HttpUrl
  ) {
    val replyFile = replyManager.getReplyFileByFileUuid(clickedAttachment.fileUUID)
      .unwrap()

    if (replyFile == null) {
      throw ClientException(
        "Failed to get reply file '${clickedAttachment.fileUUID}' " +
          "with name '${clickedAttachment.attachmentName}'"
      )
    }

    val replyFileMeta = replyFile
      .getReplyFileMeta()
      .unwrap()

    var oldFileName = replyFileMeta.fileName

    // Remove all previously added '[sound=xxx]' substrings.
    val matcher = AppConstants.SOUND_POST_PATTERN_WHOLE.matcher(replyFileMeta.fileName)
    while (matcher.find()) {
      // Do not catch exceptions here because we want to catch them in parent's exception handler
      val start = matcher.start(1)
      val end = matcher.end(1)

      oldFileName = oldFileName.removeRange(start, end)
    }

    val oldFileNameWithoutExtension = oldFileName.substringBeforeLast('.')
    val oldFileNameExtension = oldFileName.substringAfterLast('.')

    val newFileName = buildString {
      append(oldFileNameWithoutExtension)
      append("[sound=${URLEncoder.encode(uploadedFileUrl.toString(), Charsets.UTF_8.name())}]")

      if (oldFileNameExtension.isNotNullNorBlank()) {
        append(".")
        append(oldFileNameExtension)
      }
    }

    replyManager
      .updateFileName(clickedAttachment.fileUUID, newFileName, true)
      .unwrap()
  }

  private suspend fun refreshAttachedFiles() {
    val attachments = mutableListOf<Attachment>()

    replyManager.iterateNonTakenFilesOrdered { _, replyFile, replyFileMeta ->
      attachments += Attachment(
        fileUUID = replyFileMeta.fileUuid,
        imagePath = replyFile.fileOnDisk.absolutePath,
        attachmentName = replyFileMeta.fileName
      )
    }

    withContext(Dispatchers.Main) {
      Snapshot.withMutableSnapshot {
        _attachments.clear()
        _attachments.addAll(attachments)
      }
    }
  }

  @Immutable
  data class Attachment(
    val fileUUID: UUID,
    val imagePath: String,
    val attachmentName: String,
  )

  class ViewModelFactory @Inject constructor(
    private val replyManager: ReplyManager,
    private val fileHelper: FileHelper,
    private val uploadFileToCatBoxUseCase: UploadFileToCatBoxUseCase,
  ) : ViewModelAssistedFactory<CreateSoundMediaControllerViewModel> {
    override fun create(handle: SavedStateHandle): CreateSoundMediaControllerViewModel {
      return CreateSoundMediaControllerViewModel(
        savedStateHandle = handle,
        replyManager = replyManager,
        fileHelper = fileHelper,
        uploadFileToCatBoxUseCase = uploadFileToCatBoxUseCase
      )
    }
  }

  companion object {
    private const val TAG = "CreateSoundMediaControllerViewModel"
  }

}