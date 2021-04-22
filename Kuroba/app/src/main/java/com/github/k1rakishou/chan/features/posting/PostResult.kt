package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.persist_state.ReplyMode

sealed class PostResult {
  object Canceled : PostResult() {
    override fun toString(): String {
      return "Canceled"
    }
  }

  data class Error(
    val throwable: Throwable
  ) : PostResult() {
    override fun toString(): String {
      return "Error(throwable=${throwable.errorMessageOrClassName()})"
    }
  }

  data class Success(
    val replyResponse: ReplyResponse,
    val replyMode: ReplyMode,
    val retrying: Boolean
  ) : PostResult()
}