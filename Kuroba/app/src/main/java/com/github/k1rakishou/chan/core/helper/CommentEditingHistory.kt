package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.common.datastructure.RingBuffer

class CommentEditingHistory(
  private val callback: CommentEditingHistoryListener
) {
  private var firstTextWatcherEventSkipped = false

  private val commentEditingHistory = RingBuffer<CommentInputState>(128)
  private val commentEditDebouncer = Debouncer(false)

  fun updateCommentEditingHistory(commentInputState: CommentInputState) {
    if (!firstTextWatcherEventSkipped) {
      firstTextWatcherEventSkipped = true
      return
    }

    commentEditDebouncer.post({
      val last = commentEditingHistory.peek()
      if (last != null && last == commentInputState) {
        return@post
      }

      if (commentEditingHistory.isEmpty()) {
        commentEditingHistory.push(INITIAL_COMMENT_INPUT_STATE)
      }

      commentEditingHistory.push(commentInputState)
      callback.updateRevertChangeButtonVisibility(isHistoryActuallyEmpty())
    }, 150)
  }

  fun onRevertChangeButtonClicked() {
    if (isHistoryActuallyEmpty()) {
      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
      return
    }

    commentEditingHistory.pop()

    val prevCommentInputState = commentEditingHistory.peek()
    if (prevCommentInputState == null || prevCommentInputState === INITIAL_COMMENT_INPUT_STATE) {
      if (prevCommentInputState != null) {
        callback.restoreComment(prevCommentInputState)
      }

      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
      return
    }

    callback.restoreComment(prevCommentInputState)

    if (commentEditingHistory.isEmpty()) {
      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
    }
  }

  fun reset() {
    firstTextWatcherEventSkipped = false
    commentEditingHistory.clear()
  }

  private fun isHistoryActuallyEmpty(): Boolean {
    if (commentEditingHistory.isEmpty()) {
      return true
    }

    if (commentEditingHistory.size() == 1
      && commentEditingHistory.peek() === INITIAL_COMMENT_INPUT_STATE) {
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
    private val INITIAL_COMMENT_INPUT_STATE = CommentInputState("", 0, 0)
  }
}