package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class ChanPostHide(
  val postDescriptor: PostDescriptor,
  val onlyHide: Boolean,
  val applyToWholeThread: Boolean,
  val applyToReplies: Boolean,
  val manuallyRestored: Boolean
)