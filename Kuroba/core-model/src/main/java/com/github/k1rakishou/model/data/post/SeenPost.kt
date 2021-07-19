package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import org.joda.time.DateTime

data class SeenPost(
  val postDescriptor: PostDescriptor,
  val insertedAt: DateTime
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SeenPost

    if (postDescriptor != other.postDescriptor) return false

    return true
  }

  override fun hashCode(): Int {
    return postDescriptor.hashCode()
  }
}