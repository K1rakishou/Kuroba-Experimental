package com.github.k1rakishou.chan.features.reply.data

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
import com.github.k1rakishou.chan.core.site.PostFormatterButton
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope

@Stable
class ReplyLayoutState(
  val chanDescriptor: ChanDescriptor,
  private val coroutineScope: CoroutineScope,
  private val appResourcesLazy: Lazy<AppResources>,
  private val replyLayoutFileEnumeratorLazy: Lazy<ReplyLayoutFileEnumerator>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
  private val themeEngineLazy: Lazy<ThemeEngine>
) {
  private val appResources: AppResources
    get() = appResourcesLazy.get()
  private val replyLayoutFileEnumerator: ReplyLayoutFileEnumerator
    get() = replyLayoutFileEnumeratorLazy.get()
  private val boardManager: BoardManager
    get() = boardManagerLazy.get()
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val postFormattingButtonsFactory: PostFormattingButtonsFactory
    get() = postFormattingButtonsFactoryLazy.get()
  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()

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

  private val _replySendProgressState = mutableStateOf<Float?>(null)
  val replySendProgressState: State<Float?>
    get() = _replySendProgressState

  val isCatalogMode: Boolean
    get() = chanDescriptor is ChanDescriptor.ICatalogDescriptor

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    replyManager.awaitUntilFilesAreLoaded()
    Logger.debug(TAG) { "bindChanDescriptor(${chanDescriptor})" }

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

  fun collapseReplyLayout() {
    if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Collapsed) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Collapsed
    }
  }

  fun openReplyLayout() {
    if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Opened) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Opened
    }
  }

  fun expandReplyLayout() {
    if (_replyLayoutVisibility.value != ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Expanded
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

  private fun updateReplyFieldHintText() {
    _replyFieldHintText.value = formatLabelText(
      replyAttachables = _attachables.value,
      isCatalogMode = isCatalogMode,
      makeNewThreadHint = appResources.string(R.string.reply_make_new_thread_hint),
      replyInThreadHint = appResources.string(R.string.reply_reply_in_thread_hint),
      replyText = _replyText.value,
      maxCommentLength = _maxCommentLength.intValue
    )
  }

  @Suppress("ConvertTwoComparisonsToRangeCheck")
  private fun formatLabelText(
    replyAttachables: ReplyAttachables,
    isCatalogMode: Boolean,
    makeNewThreadHint: String,
    replyInThreadHint: String,
    replyText: TextFieldValue,
    maxCommentLength: Int
  ): AnnotatedString {
    val replyFileAttachables = replyAttachables.attachables
      .filterIsInstance<ReplyAttachable.ReplyFileAttachable>()

    return buildAnnotatedString {
      val commentLabelText = if (isCatalogMode) {
        makeNewThreadHint
      } else {
        replyInThreadHint
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

  companion object {
    private const val TAG = "ReplyLayoutState"
  }

}