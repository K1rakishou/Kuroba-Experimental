package com.github.adamantcheese.chan.features.drawer.data

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

data class NavigationHistoryEntry(
  val descriptor: ChanDescriptor,
  val threadThumbnailUrl: HttpUrl,
  val siteThumbnailUrl: HttpUrl?,
  val title: String,
  val additionalInfo: NavHistoryBookmarkAdditionalInfo?
)

data class NavHistoryBookmarkAdditionalInfo(
  val watching: Boolean = false,
  val newPosts: Int = 0,
  val newQuotes: Int = 0,
  val isBumpLimit: Boolean = false,
  val isImageLimit: Boolean = false,
  val isLastPage: Boolean = false
)