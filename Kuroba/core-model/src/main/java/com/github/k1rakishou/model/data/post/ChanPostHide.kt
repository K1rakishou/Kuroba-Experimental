package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class ChanPostHide(
  val filterInfo: ChanPostHideFilterInfo?,
  val postDescriptor: PostDescriptor,
  val onlyHide: Boolean,
  val applyToWholeThread: Boolean,
  val applyToReplies: Boolean,
  val manuallyRestored: Boolean
) {
  init {
    if (manuallyRestored) {
      // When a post was manually unhidden, filterInfo should be removed as well because we want to persist
      // such a ChanPostHide in the database.
      check(filterInfo == null) { "filterInfo must be null when manually restored is true" }
    }
  }

  fun createdByFilter(): Boolean {
    return filterInfo != null
  }
}

data class ChanPostHideFilterInfo(
  val filterDatabaseId: Long
)