package com.github.adamantcheese.model.data.bookmark

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

class ThreadBookmarkInfoObject(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val simplePostObjects: List<ThreadBookmarkInfoPostObject>
)

sealed class ThreadBookmarkInfoPostObject {

  fun comment(): String {
    return when (this) {
      is OriginalPost -> comment
      is RegularPost -> comment
    }
  }

  fun postNo(): Long {
    return when (this) {
      is OriginalPost -> postNo
      is RegularPost -> postNo
    }
  }

  class OriginalPost(
    val postNo: Long,
    val closed: Boolean,
    val archived: Boolean,
    val comment: String
  ) : ThreadBookmarkInfoPostObject()

  class RegularPost(
    val postNo: Long,
    val comment: String
  ) : ThreadBookmarkInfoPostObject()
}