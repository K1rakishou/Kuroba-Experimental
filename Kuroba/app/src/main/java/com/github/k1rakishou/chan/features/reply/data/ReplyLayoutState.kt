package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.runtime.IntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.PostFormatterButton
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.features.posting.PostResult
import com.github.k1rakishou.chan.features.posting.PostingCancellationException
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.chan.features.posting.PostingStatus
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Stable
class ReplyLayoutState(
  val chanDescriptor: ChanDescriptor,
  val threadControllerType: ThreadControllerType,
  private val callbacks: Callbacks,
  private val coroutineScope: CoroutineScope,
  private val appResourcesLazy: Lazy<AppResources>,
  private val replyLayoutFileEnumeratorLazy: Lazy<ReplyLayoutFileEnumerator>,
  private val siteManagerLazy: Lazy<SiteManager>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
  private val themeEngineLazy: Lazy<ThemeEngine>,
  private val globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>,
  private val postingServiceDelegateLazy: Lazy<PostingServiceDelegate>,
  private val boardFlagInfoRepositoryLazy: Lazy<BoardFlagInfoRepository>
) {
  private val appResources: AppResources
    get() = appResourcesLazy.get()
  private val replyLayoutFileEnumerator: ReplyLayoutFileEnumerator
    get() = replyLayoutFileEnumeratorLazy.get()
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

  private val _postFormatterButtons = mutableStateOf<List<PostFormatterButton>>(emptyList())
  val postFormatterButtons: State<List<PostFormatterButton>>
    get() = _postFormatterButtons

  private val _maxCommentLength = mutableIntStateOf(0)
  val maxCommentLength: State<Int>
    get() = _maxCommentLength

  private val _attachables = mutableStateOf<ReplyAttachables>(ReplyAttachables())
  val attachables: State<ReplyAttachables>
    get() = _attachables

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

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    replyManager.awaitUntilFilesAreLoaded()
    Logger.debug(TAG) { "bindChanDescriptor(${chanDescriptor})" }

    loadDraftIntoViews(chanDescriptor)
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
          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) -> ${status.javaClass.simpleName}")
        }
        is PostingStatus.Enqueued,
        is PostingStatus.WaitingForSiteRateLimitToPass,
        is PostingStatus.WaitingForAdditionalService,
        is PostingStatus.BeforePosting -> {
          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) -> ${status.javaClass.simpleName}")

          // TODO: New reply layout. This is probably not needed anymore?
//          if (::callback.isInitialized) {
//            callback.enableOrDisableReplyLayout()
//          }
        }
        is PostingStatus.UploadingProgress,
        is PostingStatus.Uploaded -> {
          // no-op
        }
        is PostingStatus.AfterPosting -> {
          Logger.d(TAG, "processPostingStatusUpdates($chanDescriptor) -> " +
            "${status.javaClass.simpleName}, status.postResult=${status.postResult}")

          // TODO: New reply layout
//          if (::callback.isInitialized) {
//            callback.enableOrDisableReplyLayout()
//          }

          when (val postResult = status.postResult) {
            PostResult.Canceled -> {
              onPostError(PostingCancellationException(chanDescriptor))
            }
            is PostResult.Error -> {
              onPostError(postResult.throwable)
            }
            is PostResult.Banned -> {
              onPostErrorBanned(postResult.banMessage, postResult.banInfo)
            }
            is PostResult.Success -> {
              onPostComplete(
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
  }

  fun insertTags(postFormatterButton: PostFormatterButton) {
    // TODO: New reply layout.
    updateReplyFieldHintText()
  }

  fun onSubjectChanged(subject: TextFieldValue) {
    _subject.value = subject
  }

  fun onNameChanged(name: TextFieldValue) {
    _name.value = name
  }

  fun onOptionsChanged(options: TextFieldValue) {
    _options.value = options
  }

  fun onSendReplyStart() {
    Logger.debug(TAG) { "onSendReplyStart(${chanDescriptor})" }
    _sendReplyState.value = SendReplyState.Started
  }

  fun onReplyEnqueued() {
    Logger.debug(TAG) { "onReplyEnqueued(${chanDescriptor})" }

    // TODO: New reply layout.
//    isExpanded = false
//    previewOpen = false
//
//    commentEditingHistory.clear()
//
//    callback.highlightPosts(emptySet())
//    callback.dialogMessage(null)
//    callback.setExpanded(expanded = false, isCleaningUp = true)
//    callback.openSubject(false)
//    callback.hideFlag()
//    callback.openNameOptions(false)
//    callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
  }

  fun onReplySent() {
    Logger.debug(TAG) { "onReplySent(${chanDescriptor})" }
    _sendReplyState.value = SendReplyState.ReplySent
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
        _postFormatterButtons.value = postFormattingButtons
      }
    }

    val replyAttachables = replyLayoutFileEnumerator.enumerate(chanDescriptor)
      .onError { error ->
        // TODO: error toast?
        Logger.error(TAG) {
          "Failed to enumerate reply files for ${chanDescriptor}, error: ${error.errorMessageOrClassName()}"
        }
      }
      .valueOrNull()

    if (replyAttachables != null) {
      _attachables.value = replyAttachables
    }

    updateReplyFieldHintText()
  }

  suspend fun loadViewsIntoDraft(chanDescriptor: ChanDescriptor): Boolean {
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
    val replyFileAttachables = replyAttachables.attachables
      .filterIsInstance<ReplyAttachable.ReplyFileAttachable>()

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

      if (replyFileAttachables.isNotEmpty()) {
        append("  ")

        val totalAttachablesCount = replyFileAttachables.size
        val selectedAttachablesCount = replyFileAttachables.count { replyFileAttachable -> replyFileAttachable.selected }
        val maxAllowedAttachablesPerPost = replyAttachables.maxAllowedAttachablesPerPost

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

  private fun onPostError(exception: Throwable) {
    BackgroundUtils.ensureMainThread()

    if (exception is CancellationException) {
      Logger.e(TAG, "onPostError: Canceled")
    } else {
      Logger.e(TAG, "onPostError", exception)
    }

    // TODO: New reply layout.
//    val errorMessage = getString(R.string.reply_error_message, exception.errorMessageOrClassName())
//    callback.dialogMessage(errorMessage)
  }

  private fun onPostErrorBanned(banMessage: CharSequence?, banInfo: ReplyResponse.BanInfo) {
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

    dialogMessage(title, message)
  }

  private suspend fun onPostComplete(
    chanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse,
    replyMode: ReplyMode,
    retrying: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    when {
      replyResponse.posted -> {
        Logger.d(TAG, "onPostComplete() posted==true replyResponse=$replyResponse")
        onPostedSuccessfully(prevChanDescriptor = chanDescriptor, replyResponse = replyResponse)
      }
      replyResponse.requireAuthentication -> {
        Logger.d(TAG, "onPostComplete() requireAuthentication==true replyResponse=$replyResponse")
        onPostCompleteUnsuccessful(
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
        Logger.d(TAG, "onPostComplete() else branch replyResponse=$replyResponse, retrying=$retrying")

        if (retrying) {
          // To avoid infinite cycles
          onPostCompleteUnsuccessful(replyResponse, additionalErrorMessage = null)
          return
        }

        when (replyResponse.additionalResponseData) {
          ReplyResponse.AdditionalResponseData.NoOp -> {
            onPostCompleteUnsuccessful(replyResponse, additionalErrorMessage = null)
          }
        }
      }
    }
  }

  private fun onPostCompleteUnsuccessful(
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

    Logger.e(TAG, "onPostCompleteUnsuccessful() error: $errorMessage")

    callbacks.dialogMessage(
      title = appResources.string(R.string.reply_layout_dialog_title),
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

    closeAll()
    highlightQuotes()
    loadDraftIntoViews(newThreadDescriptor)

    callbacks.onPostedSuccessfully(prevChanDescriptor, newThreadDescriptor)

    Logger.debug(TAG) {
      "onPostedSuccessfully(${prevChanDescriptor}) success, newThreadDescriptor: ${newThreadDescriptor}"
    }
  }

  private fun closeAll() {
    // TODO: New reply layout.
  }

  private fun highlightQuotes() {
    // TODO: New reply layout.
  }

  private fun dialogMessage(title: String?, message: CharSequence) {
    val actualTitle = title?.takeIf { it.isNotNullNorBlank() }
      ?: appResources.string(R.string.reply_layout_dialog_title)

    callbacks.dialogMessage(actualTitle, message)
  }

  interface Callbacks {
    fun showCaptcha(
      chanDescriptor: ChanDescriptor,
      replyMode: ReplyMode,
      autoReply: Boolean,
      afterPostingAttempt: Boolean
    )

    fun dialogMessage(
      title: String,
      message: CharSequence,
      onDismissListener: (() -> Unit)? = null
    )

    suspend fun onPostedSuccessfully(
      prevChanDescriptor: ChanDescriptor,
      newThreadDescriptor: ChanDescriptor.ThreadDescriptor
    )
  }

  companion object {
    private const val TAG = "ReplyLayoutState"
  }

}