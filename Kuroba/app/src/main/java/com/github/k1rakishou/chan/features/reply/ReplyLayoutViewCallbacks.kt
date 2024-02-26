package com.github.k1rakishou.chan.features.reply

import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode

interface ReplyLayoutViewCallbacks {
  suspend fun bindChanDescriptor(descriptor: ChanDescriptor, threadControllerType: ThreadControllerType)

  fun replyLayoutVisibility(): ReplyLayoutVisibility
  fun isCatalogMode(): Boolean?
  fun isExpanded(): Boolean
  fun isOpened(): Boolean
  fun isCollapsed(): Boolean
  fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility)
  fun onImageOptionsApplied()
  fun hideKeyboard()

  fun quote(post: ChanPost, withText: Boolean)
  fun quote(postDescriptor: PostDescriptor, text: CharSequence)

  fun makeSubmitCall(chanDescriptor: ChanDescriptor, replyMode: ReplyMode, retrying: Boolean)

  fun onBack(): Boolean
  fun cleanup()
}