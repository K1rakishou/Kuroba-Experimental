package com.github.k1rakishou.chan.features.reply.data

sealed class SendReplyState {
    val canCancel: Boolean
        get() {
            return when (this) {
                is ReplySent,
                is Finished -> false
                Started -> true
            }
        }

    data object Started : SendReplyState()
    data object ReplySent : SendReplyState()
    data object Finished : SendReplyState()
}