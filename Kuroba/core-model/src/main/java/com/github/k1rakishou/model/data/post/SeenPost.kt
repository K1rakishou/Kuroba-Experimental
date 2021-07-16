package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import org.joda.time.DateTime

data class SeenPost(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val postNo: Long,
  val insertedAt: DateTime
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SeenPost

    if (threadDescriptor != other.threadDescriptor) return false
    if (postNo != other.postNo) return false

    return true
  }

  override fun hashCode(): Int {
    var result = threadDescriptor.hashCode()
    result = 31 * result + postNo.hashCode()
    return result
  }

}