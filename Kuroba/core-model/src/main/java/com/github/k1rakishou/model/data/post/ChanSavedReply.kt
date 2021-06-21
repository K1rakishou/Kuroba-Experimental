package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import org.joda.time.DateTime

data class ChanSavedReply(
  val postDescriptor: PostDescriptor,
  val comment: String? = null,
  val subject: String? = null,
  val password: String? = null,
  val createdOn: DateTime = DateTime.now()
) {
  fun passwordOrEmptyString(): String = password ?: ""
}