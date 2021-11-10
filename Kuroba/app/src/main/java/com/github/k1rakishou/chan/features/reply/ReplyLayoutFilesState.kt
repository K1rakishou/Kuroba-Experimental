package com.github.k1rakishou.chan.features.reply

import com.github.k1rakishou.chan.features.reply.data.IReplyAttachable

data class ReplyLayoutFilesState(
  val isReplyLayoutExpanded: Boolean = false,
  val loading: Boolean = false,
  val attachables: List<IReplyAttachable> = emptyList()
) {
  fun isEmpty(): Boolean = attachables.isEmpty()
}