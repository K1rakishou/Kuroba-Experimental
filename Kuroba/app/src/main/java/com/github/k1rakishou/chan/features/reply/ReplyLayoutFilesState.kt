package com.github.k1rakishou.chan.features.reply

import com.github.k1rakishou.chan.features.reply.data.IReplyAttachable
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class ReplyLayoutFilesState(
  val chanDescriptor: ChanDescriptor?,
  val isReplyLayoutExpanded: Boolean = false,
  val attachables: List<IReplyAttachable> = emptyList()
) {
  fun isEmpty(): Boolean = attachables.isEmpty()
}