package com.github.adamantcheese.chan.features.bookmarks.data

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

data class ThreadBookmarkItemView(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val title: String,
  val thumbnailUrl: HttpUrl?,
  val highlight: Boolean,
  val threadBookmarkStats: ThreadBookmarkStats
)