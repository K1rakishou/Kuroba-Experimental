package com.github.adamantcheese.model.data.post

import com.github.adamantcheese.model.data.descriptor.PostDescriptor

data class ChanPostHide(
  val postDescriptor: PostDescriptor,
  val onlyHide: Boolean,
  val applyToWholeThread: Boolean,
  val applyToReplies: Boolean
)