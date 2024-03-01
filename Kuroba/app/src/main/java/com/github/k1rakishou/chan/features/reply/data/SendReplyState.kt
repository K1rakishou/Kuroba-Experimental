package com.github.k1rakishou.chan.features.reply.data

sealed class SendReplyState {
  val replyLayoutEnabled: Boolean
    get() {
      return when (this) {
        is Started -> false
        is Finished -> true
      }
    }

  val canCancel: Boolean
    get() {
      return when (this) {
        is Started -> true
        is Finished -> false
      }
    }

  data object Started : SendReplyState()
  data object Finished : SendReplyState()
}