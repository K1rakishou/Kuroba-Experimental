package com.github.adamantcheese.model.data.archive

class ArchivePost(
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
  val archivePostMediaList: MutableList<ArchivePostMedia> = mutableListOf()
) {

  var moderatorCapcode: String = ""
    set(value) {
      if (shouldFilterCapcode(value)) {
        field = ""
      } else {
        field = value
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
      "closed=$closed, archived=$archived, tripcode='$tripcode', " +
      "archivePostMediaListCount=${archivePostMediaList.size})"
  }

  fun isValid(): Boolean {
    if (postSubNo > 0L) {
      // Skip all archive ghost posts because they will fuck up the database
      return false
    }

    return postNo > 0
      && threadNo > 0
      && unixTimestampSeconds > 0
  }
}