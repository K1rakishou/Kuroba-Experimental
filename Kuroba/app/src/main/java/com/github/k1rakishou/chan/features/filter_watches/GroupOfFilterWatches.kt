package com.github.k1rakishou.chan.features.filter_watches

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

data class GroupOfFilterWatches(
  val filterPattern: String,
  val filterWatches: List<FilterWatch>
)

data class FilterWatch(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val title: String,
  val thumbnailUrl: HttpUrl?,
  val isDead: Boolean
)