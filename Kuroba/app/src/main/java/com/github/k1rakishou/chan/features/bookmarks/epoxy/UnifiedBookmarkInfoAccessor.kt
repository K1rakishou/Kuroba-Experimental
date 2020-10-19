package com.github.k1rakishou.chan.features.bookmarks.epoxy

import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

interface UnifiedBookmarkInfoAccessor {
  fun getBookmarkGroupId(): String?
  fun getBookmarkStats(): ThreadBookmarkStats?
  fun getBookmarkDescriptor(): ChanDescriptor.ThreadDescriptor?
}