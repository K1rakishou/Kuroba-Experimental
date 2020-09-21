package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class ChanSavedReply(
  val postDescriptor: PostDescriptor,
  val password: String? = null
) {
  fun passwordOrEmptyString(): String = password ?: ""
}