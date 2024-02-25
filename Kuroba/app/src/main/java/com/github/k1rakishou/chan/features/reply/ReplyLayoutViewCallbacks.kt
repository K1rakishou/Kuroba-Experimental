package com.github.k1rakishou.chan.features.reply

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.ReplyMode

interface ReplyLayoutViewCallbacks {
  fun bindChanDescriptor(descriptor: ChanDescriptor)

  fun isExpanded(chanDescriptor: ChanDescriptor): Boolean
  fun showCaptcha(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    autoReply: Boolean,
    afterPostingAttempt: Boolean,
    onFinished: ((Boolean) -> Unit)?
  )

  fun openOrCloseReplyLayout(open: Boolean)
  fun cleanup()
  fun onImageOptionsApplied()
  fun onBack(): Boolean
  fun quote(post: ChanPost, withText: Boolean)
  fun quote(postDescriptor: PostDescriptor, text: CharSequence)
}