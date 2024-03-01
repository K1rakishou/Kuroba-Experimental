package com.github.k1rakishou.chan.features.reply.data

sealed class SendReplyState {
  val canCancel: Boolean
    get() {
      return when (this) {
        is Finished -> false
        is Started -> true
      }
    }

  data object Started : SendReplyState()
  data object Finished : SendReplyState()
}