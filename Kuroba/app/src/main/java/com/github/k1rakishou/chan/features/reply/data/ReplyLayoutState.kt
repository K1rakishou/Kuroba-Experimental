package com.github.k1rakishou.chan.features.reply.data

import android.Manifest
import androidx.compose.runtime.IntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.PostFormatterButton
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.PostResult
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.chan.features.posting.PostingStatus
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

@Stable
class ReplyLayoutState(
  val chanDescriptor: ChanDescriptor,
  val threadControllerType: ThreadControllerType,
  private val callbacks: Callbacks,
  private val coroutineScope: CoroutineScope,
  private val appResourcesLazy: Lazy<AppResources>,
  private val replyLayoutHelperLazy: Lazy<ReplyLayoutHelper>,
  private val siteManagerLazy: Lazy<SiteManager>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
  private val themeEngineLazy: Lazy<ThemeEngine>,
  private val globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>,
  private val postingServiceDelegateLazy: Lazy<PostingServiceDelegate>,
  private val boardFlagInfoRepositoryLazy: Lazy<BoardFlagInfoRepository>,
  private val runtimePermissionsHelperLazy: Lazy<RuntimePermissionsHelper>,
  private val imagePickHelperLazy: Lazy<ImagePickHelper>
) {
  private val appResources: AppResources
    get() = appResourcesLazy.get()
  private val replyLayoutHelper: ReplyLayoutHelper
    get() = replyLayoutHelperLazy.get()
  private val siteManager: SiteManager
    get() = siteManagerLazy.get()
  private val boardManager: BoardManager
    get() = boardManagerLazy.get()
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val postFormattingButtonsFactory: PostFormattingButtonsFactory
    get() = postFormattingButtonsFactoryLazy.get()
  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  private val globalUiStateHolder: GlobalUiStateHolder
    get() = globalUiStateHolderLazy.get()
  private val postingServiceDelegate: PostingServiceDelegate
    get() = postingServiceDelegateLazy.get()
  private val boardFlagInfoRepository: BoardFlagInfoRepository
    get() = boardFlagInfoRepositoryLazy.get()
  private val runtimePermissionsHelper: RuntimePermissionsHelper
    get() = runtimePermissionsHelperLazy.get()
  private val imagePickHelper: ImagePickHelper
    get() = imagePickHelperLazy.get()

  private val _replyText = mutableStateOf<TextFieldValue>(TextFieldValue())
  val replyText: State<TextFieldValue>
    get() = _replyText

  private val _subject = mutableStateOf<TextFieldValue>(TextFieldValue())
  val subject: State<TextFieldValue>
    get() = _subject

  private val _name = mutableStateOf<TextFieldValue>(TextFieldValue())
  val name: State<TextFieldValue>
    get() = _name

  private val _replyFieldHintText = mutableStateOf<AnnotatedString>(AnnotatedString(""))
  val replyFieldHintText: State<AnnotatedString>
    get() = _replyFieldHintText

  private val _options = mutableStateOf<TextFieldValue>(TextFieldValue())
  val options: State<TextFieldValue>
    get() = _options

  private val _syntheticAttachables = mutableStateListOf<SyntheticReplyAttachable>()
  val syntheticAttachables: List<SyntheticReplyAttachable>
    get() = _syntheticAttachables

  private val _attachables = mutableStateOf<ReplyAttachables>(ReplyAttachables())
  val attachables: State<ReplyAttachables>
    get() = _attachables

  private val _postFormatterButtons = mutableStateOf<List<PostFormatterButton>>(emptyList())
  val postFormatterButtons: State<List<PostFormatterButton>>
    get() = _postFormatterButtons

  private val _maxCommentLength = mutableIntStateOf(0)
  val maxCommentLength: State<Int>
    get() = _maxCommentLength

  private val _replyLayoutAnimationState = mutableStateOf<ReplyLayoutAnimationState>(ReplyLayoutAnimationState.Collapsed)
  val replyLayoutAnimationState: State<ReplyLayoutAnimationState>
    get() = _replyLayoutAnimationState

  private val _replyLayoutVisibility = mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed)
  val replyLayoutVisibility: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibility

  private val _sendReplyState = mutableStateOf<SendReplyState>(SendReplyState.Finished)
  val sendReplyState: State<SendReplyState>
    get() = _sendReplyState

  private val _replySendProgressInPercentsState = mutableIntStateOf(-1)
  val replySendProgressInPercentsState: IntState
    get() = _replySendProgressInPercentsState

  val isCatalogMode: Boolean
    get() = threadControllerType == ThreadControllerType.Catalog

  private val filePickerExecutor = RendezvousCoroutineExecutor(coroutineScope)

  private var persistInReplyManagerJob: Job? = null
  private var listenForReplyManagerUpdatesJob: Job? = null
  private var listenForNewPickedFilesJob: Job? = null
  private var listenForSyntheticFilesUpdatesJob: Job? = null

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    replyManager.awaitUntilFilesAreLoaded()
    Logger.debug(TAG) { "bindChanDescriptor(${chanDescriptor})" }

    loadDraftIntoViews(chanDescriptor)

    listenForReplyManagerUpdatesJob = coroutineScope.launch {
      replyManager.listenForReplyFilesUpdates()
        .onEach { updateAttachables() }
        .collect()
    }

    listenForNewPickedFilesJob = coroutineScope.launch {
      imagePickHelper.pickedFilesUpdateFlow
        .onEach { updateAttachables() }
        .collect()
    }

    listenForSyntheticFilesUpdatesJob = coroutineScope.launch {
      imagePickHelper.syntheticFilesUpdatesFlow
        .onEach { syntheticReplyAttachable ->
          when (syntheticReplyAttachable.state) {
            SyntheticReplyAttachableState.Initializing,
            SyntheticReplyAttachableState.Downloading,
            SyntheticReplyAttachableState.Decoding -> {
              val index = _syntheticAttachables
                .indexOfFirst { attachable -> attachable.id == syntheticReplyAttachable.id }

              if (index >= 0) {
                _syntheticAttachables[index] = syntheticReplyAttachable
              } else {
                _syntheticAttachables.add(0, syntheticReplyAttachable)
              }
            }
            SyntheticReplyAttachableState.Done -> {
              _syntheticAttachables.removeIfKt { attachable -> attachable.id == syntheticReplyAttachable.id }
            }
          }
        }
        .collect()
    }
  }

  fun unbindChanDescriptor() {
    persistInReplyManagerJob?.cancel()
    persistInReplyManagerJob = null

    listenForReplyManagerUpdatesJob?.cancel()
    listenForReplyManagerUpdatesJob = null

    listenForNewPickedFilesJob?.cancel()
    listenForNewPickedFilesJob = null

    listenForSyntheticFilesUpdatesJob?.cancel()
    listenForSyntheticFilesUpdatesJob = null
  }

  suspend fun onPostingStatusEvent(status: PostingStatus) {
    withContext(Dispatchers.Main) {
      if (status.chanDescriptor != chanDescriptor) {
        // The user may open another thread while the reply is being uploaded so we need to check
        // whether this even actually belongs to this catalog/thread.
        return@withContext
      }

      when (status) {
        is PostingStatus.Attached -> {
          Logger.d(TAG, "processPostingStatusUpdates(${status.chanDescriptor}) -> ${status.javaClass.simpleName}")
        }
        is PostingStatus.Enqueued,
        is PostingStatus.WaitingForSiteRateLimitToPass,
        is PostingStatus.WaitingForAdditionalService,
        is PostingStatus.BeforePosting -> {
          Logger.d(TAG, "processPostingStatusUpdates(${status.chanDescriptor}) -> ${status.javaClass.simpleName}")
        }
        is PostingStatus.UploadingProgress,
        is PostingStatus.Uploaded -> {
          // no-op
        }
        is PostingStatus.AfterPosting -> {
          Logger.d(TAG, "processPostingStatusUpdates(${status.chanDescriptor}) -> " +
            "${status.javaClass.simpleName}, status.postResult: ${status.postResult}")

          onSendReplyEnd()

          when (val postResult = status.postResult) {
            PostResult.Canceled -> {
              onPostSendCanceled(chanDescriptor = status.chanDescriptor)
            }
            is PostResult.Error -> {
              onPostSendError(
                chanDescriptor = status.chanDescriptor,
                exception = postResult.throwable
              )
            }
            is PostResult.Banned -> {
              onPostSendErrorBanned(postResult.banMessage, postResult.banInfo)
            }
            is PostResult.Success -> {
              onPostSendComplete(
                chanDescriptor = status.chanDescriptor,
                replyResponse = postResult.replyResponse,
                replyMode = postResult.replyMode,
                retrying = postResult.retrying
              )
            }
          }

          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) consumeTerminalEvent(${status.chanDescriptor})")
          postingServiceDelegate.consumeTerminalEvent(status.chanDescriptor)
        }
      }
    }
  }

  fun onHeightChanged(newHeight: Int) {
    onHeightChangedInternal(newHeight)
  }

  fun isReplyLayoutExpanded(): Boolean {
    return _replyLayoutVisibility.value == ReplyLayoutVisibility.Expanded
  }

  fun collapseReplyLayout() {
    if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Collapsed) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Collapsed
      onReplyLayoutVisibilityChangedInternal(ReplyLayoutVisibility.Collapsed)
    }
  }

  fun openReplyLayout() {
    if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Opened) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityChangedInternal(ReplyLayoutVisibility.Opened)
    }
  }

  fun expandReplyLayout() {
    if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Expanded
      onReplyLayoutVisibilityChangedInternal(ReplyLayoutVisibility.Expanded)
    }
  }

  fun onReplyTextChanged(replyText: TextFieldValue) {
    _replyText.value = replyText
    updateReplyFieldHintText()
    persistInReplyManager()
  }

  fun insertTags(postFormatterButton: PostFormatterButton) {
    // TODO: New reply layout.
    updateReplyFieldHintText()
    persistInReplyManager()
  }

  fun onSubjectChanged(subject: TextFieldValue) {
    _subject.value = subject
    persistInReplyManager()
  }

  fun onNameChanged(name: TextFieldValue) {
    _name.value = name
    persistInReplyManager()
  }

  fun onOptionsChanged(options: TextFieldValue) {
    _options.value = options
    persistInReplyManager()
  }

  fun removeAttachedMedia(attachedMedia: ReplyFileAttachable) {
    replyManager.deleteFile(
      fileUuid = attachedMedia.fileUuid,
      notifyListeners = true
    ).onError { error ->
      Logger.error(TAG) { "removeAttachedMedia(${attachedMedia.fileUuid}) error: ${error.errorMessageOrClassName()}" }
    }.ignore()
  }

  fun onAttachableSelectionChanged(attachedMedia: ReplyFileAttachable, selected: Boolean) {
    replyManager.updateFileSelection(
      fileUuid = attachedMedia.fileUuid,
      selected = selected,
      notifyListeners = true
    ).onError { error ->
      Logger.error(TAG) { "onAttachableSelectionChanged(${attachedMedia.fileUuid}, ${selected}) " +
        "error: ${error.errorMessageOrClassName()}" }
    }.ignore()
  }

  fun pickLocalMedia(showFilePickerChooser: Boolean) {
    filePickerExecutor.post {
      if (!requestPermissionIfNeededSuspend()) {
        callbacks.showToast(appResources.string(R.string.reply_layout_pick_file_permission_required))
        return@post
      }

      try {
        val input = LocalFilePicker.LocalFilePickerInput(
          notifyListeners = false,
          replyChanDescriptor = chanDescriptor,
          clearLastRememberedFilePicker = showFilePickerChooser
        )

        val pickedFileResult = withContext(Dispatchers.IO) { imagePickHelper.pickLocalFile(input) }
          .unwrap()

        val replyFiles = (pickedFileResult as PickedFile.Result).replyFiles
        replyFiles.forEach { replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "pickLocalMedia() imagePickHelper.pickLocalFile($chanDescriptor) getReplyFileMeta() error", error)
            return@forEach
          }

          val maxAllowedFilesPerPost = replyLayoutHelper.getMaxAllowedFilesPerPost(chanDescriptor)
          if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
            replyManager.updateFileSelection(
              fileUuid = replyFileMeta.fileUuid,
              selected = true,
              notifyListeners = true
            )
          }
        }

        Logger.d(TAG, "pickLocalMedia() success")
      } catch (error: Throwable) {
        Logger.error(TAG) { "pickLocalMedia() error: ${error.errorMessageOrClassName()}" }

        // TODO: New reply layout. Error toast.
      }
    }
  }


  fun pickRemoteMedia(selectedImageUrl: HttpUrl) {
    filePickerExecutor.post {
      try {
        val selectedImageUrlString = selectedImageUrl.toString()

        val input = RemoteFilePicker.RemoteFilePickerInput(
          notifyListeners = true,
          replyChanDescriptor = chanDescriptor,
          imageUrls = listOf(selectedImageUrlString)
        )

        val pickedFileResult = withContext(Dispatchers.IO) { imagePickHelper.pickRemoteFile(input) }
          .unwrap()

        val replyFiles = (pickedFileResult as PickedFile.Result).replyFiles
        replyFiles.forEach { replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "pickLocalMedia() imagePickHelper.pickRemoteMedia($chanDescriptor) getReplyFileMeta() error", error)
            return@forEach
          }

          val maxAllowedFilesPerPost = replyLayoutHelper.getMaxAllowedFilesPerPost(chanDescriptor)
          if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
            replyManager.updateFileSelection(
              fileUuid = replyFileMeta.fileUuid,
              selected = true,
              notifyListeners = true
            )
          }
        }

        Logger.d(TAG, "pickRemoteMedia() success")
      } catch (error: Throwable) {
        Logger.error(TAG) { "pickRemoteMedia() error: ${error.errorMessageOrClassName()}" }

        // TODO: New reply layout. Error toast.
      }
    }
  }

  fun onSendReplyStart() {
    Logger.debug(TAG) { "onSendReplyStart(${chanDescriptor})" }
    _sendReplyState.value = SendReplyState.Started
  }

  fun onReplyEnqueued() {
    Logger.debug(TAG) { "onReplyEnqueued(${chanDescriptor})" }

    if (isReplyLayoutExpanded()) {
      openReplyLayout()
    }

    callbacks.hideDialog()
  }

  fun onSendReplyEnd() {
    Logger.debug(TAG) { "onSendReplyEnd(${chanDescriptor})" }
    _sendReplyState.value = SendReplyState.Finished
  }

  suspend fun loadDraftIntoViews(chanDescriptor: ChanDescriptor) {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Collapsed
      return
    }

    val postFormattingButtons = postFormattingButtonsFactory.createPostFormattingButtons(chanDescriptor.boardDescriptor())

    replyManager.readReply(chanDescriptor) { reply ->
      _replyText.value = TextFieldValue(
        text = reply.comment,
        selection = TextRange(reply.comment.length)
      )

      _subject.value = TextFieldValue(
        text = reply.subject,
        selection = TextRange(reply.subject.length)
      )

      _name.value = TextFieldValue(
        text = reply.postName,
        selection = TextRange(reply.postName.length)
      )

      _options.value = TextFieldValue(
        text = reply.options,
        selection = TextRange(reply.options.length)
      )

      boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())?.let { chanBoard ->
        _maxCommentLength.intValue = chanBoard.maxCommentChars
      }

      _postFormatterButtons.value = postFormattingButtons
    }

    updateAttachables()
  }

  suspend fun loadViewsIntoDraft(): Boolean {
    val lastUsedFlagKey = boardFlagInfoRepository.getLastUsedFlagKey(chanDescriptor.boardDescriptor())

    replyManager.readReply(chanDescriptor) { reply ->
      reply.comment = replyText.value.text
      reply.postName = name.value.text
      reply.subject = subject.value.text
      reply.options = options.value.text

      if (lastUsedFlagKey.isNotNullNorEmpty()) {
        reply.flag = lastUsedFlagKey
      }
    }

    return true
  }

  private fun canAutoSelectFile(maxAllowedFilesPerPost: Int): ModularResult<Boolean> {
    return Try { replyManager.selectedFilesCount().unwrap() < maxAllowedFilesPerPost }
  }

  private suspend fun requestPermissionIfNeededSuspend(): Boolean {
    if (AndroidUtils.isAndroid13()) {
      // Can't request READ_EXTERNAL_STORAGE on API 33+
      return true
    }

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

  private suspend fun updateAttachables() {
    val replyAttachables = replyLayoutHelper.enumerateReplyFiles(chanDescriptor)
      .onError { error ->
        Logger.error(TAG) {
          "updateAttachables() Failed to enumerate reply files for ${chanDescriptor}, error: ${error.errorMessageOrClassName()}"
        }

        // TODO: strings
        callbacks.showToast("Error while enumerating files: ${error.errorMessageOrClassName()}")
      }
      .valueOrNull()

    if (replyAttachables != null) {
      _attachables.value = replyAttachables
    }

    updateReplyFieldHintText()
  }

  private fun persistInReplyManager() {
    persistInReplyManagerJob?.cancel()
    persistInReplyManagerJob = coroutineScope.launch(Dispatchers.Default) {
      delay(100)
      loadViewsIntoDraft()
    }
  }

  private fun onHeightChangedInternal(
    newHeight: Int
  ) {
    globalUiStateHolder.updateReplyLayoutGlobalState { replyLayoutGlobalState ->
      replyLayoutGlobalState.update(threadControllerType) { individualReplyLayoutGlobalState ->
        individualReplyLayoutGlobalState.updateCurrentReplyLayoutHeight(newHeight)
      }
    }
  }

  private fun onReplyLayoutVisibilityChangedInternal(replyLayoutVisibility: ReplyLayoutVisibility) {
    globalUiStateHolder.updateReplyLayoutGlobalState { replyLayoutGlobalState ->
      replyLayoutGlobalState.update(threadControllerType) { individualReplyLayoutGlobalState ->
        individualReplyLayoutGlobalState.updateReplyLayoutVisibility(replyLayoutVisibility)
      }
    }
  }

  private fun updateReplyFieldHintText() {
    _replyFieldHintText.value = formatLabelText(
      replyAttachables = _attachables.value,
      threadControllerType = threadControllerType,
      makeNewThreadHint = appResources.string(R.string.reply_make_new_thread_hint),
      replyInThreadHint = appResources.string(R.string.reply_reply_in_thread_hint),
      replyText = _replyText.value,
      maxCommentLength = _maxCommentLength.intValue
    )

    // TODO: New reply layout. Highlight posts with quotes from reply text.
  }

  @Suppress("ConvertTwoComparisonsToRangeCheck")
  private fun formatLabelText(
    replyAttachables: ReplyAttachables,
    threadControllerType: ThreadControllerType,
    makeNewThreadHint: String,
    replyInThreadHint: String,
    replyText: TextFieldValue,
    maxCommentLength: Int
  ): AnnotatedString {
    return buildAnnotatedString {
      val commentLabelText = when (threadControllerType) {
        ThreadControllerType.Catalog -> makeNewThreadHint
        ThreadControllerType.Thread -> replyInThreadHint
      }

      append(commentLabelText)

      append(" ")

      val commentLength = replyText.text.length

      if (maxCommentLength > 0 && commentLength > maxCommentLength) {
        withStyle(SpanStyle(color = themeEngine.chanTheme.errorColorCompose)) {
          append(commentLength.toString())
        }
      } else {
        append(commentLength.toString())
      }

      if (maxCommentLength > 0) {
        append("/")
        append(maxCommentLength.toString())
      }

      if (replyAttachables.attachables.isNotEmpty()) {
        append("  ")

        val selectedAttachablesCount = replyAttachables.attachables.count { replyFileAttachable -> replyFileAttachable.selected }
        val maxAllowedAttachablesPerPost = replyAttachables.maxAllowedAttachablesPerPost
        val totalAttachablesCount = replyAttachables.attachables.size

        if (maxAllowedAttachablesPerPost > 0 && selectedAttachablesCount > maxAllowedAttachablesPerPost) {
          withStyle(SpanStyle(color = themeEngine.chanTheme.errorColorCompose)) {
            append(selectedAttachablesCount.toString())
          }
        } else {
          append(selectedAttachablesCount.toString())
        }

        if (maxAllowedAttachablesPerPost > 0) {
          append("/")
          append(maxAllowedAttachablesPerPost.toString())
        }

        append(" ")
        append("(")
        append(totalAttachablesCount.toString())
        append(")")
      }
    }
  }

  private fun onPostSendCanceled(chanDescriptor: ChanDescriptor) {
    Logger.debug(TAG) { "onPostSendCanceled(${chanDescriptor})" }
    callbacks.showToast(appResources.string(R.string.reply_send_canceled_by_user))
  }

  private fun onPostSendError(chanDescriptor: ChanDescriptor, exception: Throwable) {
    BackgroundUtils.ensureMainThread()
    Logger.e(TAG, "onPostSendError(${chanDescriptor})", exception)

    showDialog(
      message = appResources.string(R.string.reply_error_message, exception.errorMessageOrClassName())
    )
  }

  private fun onPostSendErrorBanned(banMessage: CharSequence?, banInfo: ReplyResponse.BanInfo) {
    val title = when (banInfo) {
      ReplyResponse.BanInfo.Banned -> appResources.string(R.string.reply_layout_info_title_ban_info)
      ReplyResponse.BanInfo.Warned -> appResources.string(R.string.reply_layout_info_title_warning_info)
    }

    val message = if (banMessage.isNotNullNorBlank()) {
      banMessage
    } else {
      when (banInfo) {
        ReplyResponse.BanInfo.Banned -> appResources.string(R.string.post_service_response_probably_banned)
        ReplyResponse.BanInfo.Warned -> appResources.string(R.string.post_service_response_probably_warned)
      }
    }

    showDialog(title, message)
  }

  private suspend fun onPostSendComplete(
    chanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse,
    replyMode: ReplyMode,
    retrying: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    when {
      replyResponse.posted -> {
        Logger.d(TAG, "onPostSendComplete(${chanDescriptor}) posted is true replyResponse: $replyResponse")
        onPostedSuccessfully(
          prevChanDescriptor = chanDescriptor,
          replyResponse = replyResponse
        )
      }
      replyResponse.requireAuthentication -> {
        Logger.d(TAG, "onPostSendComplete(${chanDescriptor}) requireAuthentication os true replyResponse: $replyResponse")
        onPostCompleteUnsuccessful(
          chanDescriptor = chanDescriptor,
          replyResponse = replyResponse,
          additionalErrorMessage = null,
          onDismissListener = {
            callbacks.showCaptcha(
              chanDescriptor = chanDescriptor,
              replyMode = replyMode,
              autoReply = true,
              afterPostingAttempt = true
            )
          }
        )
      }
      else -> {
        Logger.d(TAG, "onPostSendComplete(${chanDescriptor}) else branch replyResponse: $replyResponse, retrying: $retrying")

        if (retrying) {
          // To avoid infinite cycles
          onPostCompleteUnsuccessful(
            chanDescriptor = chanDescriptor,
            replyResponse = replyResponse,
            additionalErrorMessage = null
          )

          return
        }

        when (replyResponse.additionalResponseData) {
          ReplyResponse.AdditionalResponseData.NoOp -> {
            onPostCompleteUnsuccessful(
              chanDescriptor = chanDescriptor,
              replyResponse = replyResponse,
              additionalErrorMessage = null
            )
          }
        }
      }
    }
  }

  private fun onPostCompleteUnsuccessful(
    chanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse,
    additionalErrorMessage: String? = null,
    onDismissListener: (() -> Unit)? = null
  ) {
    val errorMessage = when {
      additionalErrorMessage != null -> {
        appResources.string(R.string.reply_error_message, additionalErrorMessage)
      }
      replyResponse.errorMessageShort != null -> {
        appResources.string(R.string.reply_error_message, replyResponse.errorMessageShort!!)
      }
      replyResponse.requireAuthentication -> {
        val errorMessage = if (replyResponse.errorMessageShort.isNotNullNorBlank()) {
          replyResponse.errorMessageShort!!
        } else {
          appResources.string(R.string.reply_error_authentication_required)
        }

        appResources.string(R.string.reply_error_message, errorMessage)
      }
      else -> appResources.string(R.string.reply_error_unknown, replyResponse.asFormattedText())
    }

    Logger.e(TAG, "onPostCompleteUnsuccessful(${chanDescriptor}) error: $errorMessage")

    showDialog(
      message = errorMessage,
      onDismissListener = onDismissListener
    )
  }

  private suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ) {
    val siteDescriptor = replyResponse.siteDescriptor

    Logger.debug(TAG) {
      "onPostedSuccessfully(${prevChanDescriptor}) siteDescriptor: $siteDescriptor, replyResponse: $replyResponse"
    }

    if (siteDescriptor == null) {
      Logger.error(TAG) {
        "onPostedSuccessfully(${prevChanDescriptor}) siteDescriptor is null"
      }

      return
    }

    // if the thread being presented has changed in the time waiting for this call to
    // complete, the loadable field in ReplyPresenter will be incorrect; reconstruct
    // the loadable (local to this method) from the reply response
    val localSite = siteManager.bySiteDescriptor(siteDescriptor)
    if (localSite == null) {
      Logger.error(TAG) {
        "onPostedSuccessfully(${prevChanDescriptor}) localSite is null"
      }

      return
    }

    val boardDescriptor = BoardDescriptor.create(
      siteDescriptor = siteDescriptor,
      boardCode = replyResponse.boardCode
    )

    val localBoard = boardManager.byBoardDescriptor(boardDescriptor)
    if (localBoard == null) {
      Logger.error(TAG) {
        "onPostedSuccessfully(${prevChanDescriptor}) localBoard is null"
      }

      return
    }

    val threadNo = if (replyResponse.threadNo <= 0L) {
      replyResponse.postNo
    } else {
      replyResponse.threadNo
    }

    val newThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      siteName = localSite.name(),
      boardCode = localBoard.boardCode(),
      threadNo = threadNo
    )

    callbacks.hideDialog()
    collapseReplyLayout()
    loadDraftIntoViews(newThreadDescriptor)

    callbacks.onPostedSuccessfully(prevChanDescriptor, newThreadDescriptor)

    Logger.debug(TAG) {
      "onPostedSuccessfully(${prevChanDescriptor}) success, newThreadDescriptor: ${newThreadDescriptor}"
    }
  }

  private fun showDialog(message: CharSequence, onDismissListener: (() -> Unit)? = null) {
    val title = appResources.string(R.string.reply_layout_dialog_title)
    showDialog(title, message, onDismissListener)
  }

  private fun showDialog(title: String, message: CharSequence, onDismissListener: (() -> Unit)? = null) {
    callbacks.showDialog(title, message, onDismissListener)
  }

  suspend fun attachableFileStatus(replyFileAttachable: ReplyFileAttachable): AnnotatedString {
    return replyLayoutHelper.attachableFileStatus(
      chanDescriptor = chanDescriptor,
      chanTheme = themeEngine.chanTheme,
      clickedFile = replyFileAttachable
    )
  }

  interface Callbacks {
    fun showCaptcha(
      chanDescriptor: ChanDescriptor,
      replyMode: ReplyMode,
      autoReply: Boolean,
      afterPostingAttempt: Boolean
    )

    fun showDialog(
      title: String,
      message: CharSequence,
      onDismissListener: (() -> Unit)? = null
    )

    fun hideDialog()

    fun showToast(message: String)

    suspend fun onPostedSuccessfully(
      prevChanDescriptor: ChanDescriptor,
      newThreadDescriptor: ChanDescriptor.ThreadDescriptor
    )

  }

  companion object {
    private const val TAG = "ReplyLayoutState"
  }

}