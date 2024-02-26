package com.github.k1rakishou.chan.features.reply

import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost

interface ReplyLayoutViewCallbacks {
  suspend fun bindChanDescriptor(descriptor: ChanDescriptor)

  fun replyLayoutVisibility(): ReplyLayoutVisibility
  fun isExpanded(): Boolean
  fun isOpened(): Boolean
  fun isCollapsed(): Boolean
  fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility)

  fun cleanup()
  fun onImageOptionsApplied()
  fun onBack(): Boolean
  fun quote(post: ChanPost, withText: Boolean)
  fun quote(postDescriptor: PostDescriptor, text: CharSequence)
}