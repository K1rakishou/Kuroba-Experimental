package com.github.adamantcheese.model.data.post

import com.github.adamantcheese.model.data.descriptor.PostDescriptor

data class ChanSavedReply(
  val postDescriptor: PostDescriptor,
  val password: String? = null
) {
  fun passwordOrEmptyString(): String = password ?: ""
}