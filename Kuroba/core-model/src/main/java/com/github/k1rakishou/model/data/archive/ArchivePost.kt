package com.github.k1rakishou.model.data.archive

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class ArchivePost(
  private val boardDescriptor: BoardDescriptor,
  var postNo: Long = -1L,
  var postSubNo: Long = 0L,
  var threadNo: Long = -1L,
  var isOP: Boolean = false,
  var unixTimestampSeconds: Long = -1L,
  var name: String = "",
  var subject: String = "",
  var comment: String = "",
  var sticky: Boolean = false,
  var closed: Boolean = false,
  var archived: Boolean = false,
  var tripcode: String = "",
  var posterId: String = "",
  val archivePostMediaList: MutableList<ArchivePostMedia> = mutableListOf()
) {

  val postDescriptor by lazy {
    check(postNo >= 0L) { "Bad postNo" }
    check(threadNo >= 0L) { "Bad threadNo" }

    PostDescriptor.create(boardDescriptor, threadNo, postNo, postSubNo)
  }

  var moderatorCapcode: String = ""
    set(value) {
      field = if (shouldFilterCapcode(value)) {
        ""
      } else {
        value
      }
    }

  private fun shouldFilterCapcode(value: String): Boolean {
    return when (value) {
      // Archived.moe returns capcode field with "N" symbols for every single post. I have
      // no idea what this means but I suppose it's the same as no capcode.
      "N" -> true
      else -> false
    }
  }

  override fun toString(): String {
    return "ArchivePost(postNo=$postNo, postSubNo=$postSubNo, threadNo=$threadNo, isOP=$isOP, " +
      "unixTimestampSeconds=$unixTimestampSeconds, moderatorCapcode='$moderatorCapcode'," +
      " name='$name', subject='$subject', comment='$comment', sticky=$sticky, " +
      "closed=$closed, archived=$archived, tripcode='$tripcode', posterId='$posterId'" +
      "archivePostMediaListCount=${archivePostMediaList.size})"
  }

  fun isValid(): Boolean {
    if (postSubNo > 0L) {
      // Skip all archive ghost posts because we don't support them yet
      return false
    }

    return postNo > 0
      && threadNo > 0
      && unixTimestampSeconds > 0
  }
}