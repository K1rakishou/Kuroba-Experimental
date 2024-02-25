package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.PostFormatterButton
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope

@Stable
class ReplyLayoutState(
  val chanDescriptor: ChanDescriptor,
  private val coroutineScope: CoroutineScope,
  private val replyLayoutFileEnumeratorLazy: Lazy<ReplyLayoutFileEnumerator>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>
) {
  private val _replyText = mutableStateOf<TextFieldValue>(TextFieldValue())
  val replyText: State<TextFieldValue>
    get() = _replyText

  private val _subject = mutableStateOf<TextFieldValue>(TextFieldValue())
  val subject: State<TextFieldValue>
    get() = _subject

  private val _name = mutableStateOf<TextFieldValue>(TextFieldValue())
  val name: State<TextFieldValue>
    get() = _name

  private val _options = mutableStateOf<TextFieldValue>(TextFieldValue())
  val options: State<TextFieldValue>
    get() = _options

  private val _postFormatterButtons = mutableStateOf<List<PostFormatterButton>>(emptyList())
  val postFormatterButtons: State<List<PostFormatterButton>>
    get() = _postFormatterButtons

  private val _maxCommentLength = mutableIntStateOf(0)
  val maxCommentLength: State<Int>
    get() = _maxCommentLength

  private val _attachables = mutableStateListOf<ReplyAttachable>()
  val attachables: List<ReplyAttachable>
    get() = _attachables

  private val _replyLayoutVisibility = mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed)
  val replyLayoutVisibility: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibility

  private val _sendReplyState = mutableStateOf<SendReplyState>(SendReplyState.Finished)
  val sendReplyState: State<SendReplyState>
    get() = _sendReplyState

  private val _replySendProgressState = mutableStateOf<Float?>(null)
  val replySendProgressState: State<Float?>
    get() = _replySendProgressState

  private val replyLayoutFileEnumerator: ReplyLayoutFileEnumerator
    get() = replyLayoutFileEnumeratorLazy.get()
  private val boardManager: BoardManager
    get() = boardManagerLazy.get()
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val postFormattingButtonsFactory: PostFormattingButtonsFactory
    get() = postFormattingButtonsFactoryLazy.get()

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
      _attachables.clear()
      _attachables.addAll(replyAttachables)
    }
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

  fun insertTags(postFormatterButton: PostFormatterButton) {
    // TODO: New reply layout.
  }

  companion object {
    private const val TAG = "ReplyLayoutState"
  }

}