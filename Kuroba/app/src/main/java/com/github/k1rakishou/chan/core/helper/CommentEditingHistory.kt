package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.common.datastructure.RingBuffer

class CommentEditingHistory(
  private val callback: CommentEditingHistoryListener
) {
  private var firstTextWatcherEventSkipped = false

  private val buffer = RingBuffer<CommentInputState>(128)
  private val debouncer = Debouncer(false)

  fun updateCommentEditingHistory(commentInputState: CommentInputState) {
    if (!firstTextWatcherEventSkipped) {
      firstTextWatcherEventSkipped = true
      return
    }

    debouncer.post({
      val last = buffer.peek()
      if (last != null && last == commentInputState) {
        return@post
      }

      if (buffer.isEmpty()) {
        buffer.push(INITIAL_COMMENT_INPUT_STATE)
      }

      buffer.push(commentInputState)
      callback.updateRevertChangeButtonVisibility(isHistoryActuallyEmpty())
    }, DEBOUNCE_TIME)
  }

  fun onRevertChangeButtonClicked() {
    if (isHistoryActuallyEmpty()) {
      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
      return
    }

    buffer.pop()

    val prevCommentInputState = buffer.peek()
    if (prevCommentInputState == null || prevCommentInputState === INITIAL_COMMENT_INPUT_STATE) {
      if (prevCommentInputState != null) {
        callback.restoreComment(prevCommentInputState)
      }

      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
      return
    }

    callback.restoreComment(prevCommentInputState)

    if (buffer.isEmpty()) {
      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
    }
  }

  fun reset() {
    firstTextWatcherEventSkipped = false
    buffer.clear()
  }

  fun clear(lastCommentState: CommentInputState) {
    buffer.clear()
    buffer.push(lastCommentState)

    callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
  }

  private fun isHistoryActuallyEmpty(): Boolean {
    if (buffer.isEmpty()) {
      return true
    }

    if (buffer.size() == 1
      && buffer.peek() === INITIAL_COMMENT_INPUT_STATE) {
      return true
    }

    return false
  }

  interface CommentEditingHistoryListener {
    fun updateRevertChangeButtonVisibility(isBufferEmpty: Boolean)
    fun restoreComment(prevCommentInputState: CommentInputState)
  }

  data class CommentInputState(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
  )

  companion object {
    private const val DEBOUNCE_TIME = 150L

    private val INITIAL_COMMENT_INPUT_STATE = CommentInputState("", 0, 0)
  }
}