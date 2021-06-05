package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.common.datastructure.RingBuffer

class CommentEditingHistory(
  private val callback: CommentEditingHistoryListener
) {
  private val buffer = RingBuffer<CommentInputState>(128)
  private val debouncer = Debouncer(false)

  private var initialCommentInputState: CommentInputState? = null

  fun updateInitialCommentEditingHistory(commentInputState: CommentInputState) {
    if (buffer.isEmpty()) {
      buffer.push(commentInputState)
    }

    if (initialCommentInputState == null) {
      initialCommentInputState = commentInputState
    }
  }

  fun updateCommentEditingHistory(commentInputState: CommentInputState) {
    debouncer.post({
      val last = buffer.peek()
      if (last != null && last == commentInputState) {
        return@post
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
    if (prevCommentInputState == null) {
      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
      return
    }

    callback.restoreComment(prevCommentInputState)

    if (buffer.isEmpty() || prevCommentInputState == initialCommentInputState) {
      initialCommentInputState = null
      callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
    }
  }

  fun clear() {
    buffer.clear()
    initialCommentInputState = null

    callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
  }

  private fun isHistoryActuallyEmpty(): Boolean {
    if (buffer.isEmpty()) {
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
  ) {

    @Suppress("ConvertTwoComparisonsToRangeCheck")
    fun isSelectionValid(): Boolean {
      return selectionStart >= 0 && selectionEnd >= selectionStart
    }

  }

  companion object {
    private const val DEBOUNCE_TIME = 150L
  }
}