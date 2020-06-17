package com.github.adamantcheese.chan.features.drawer.data

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

data class NavigationHistoryEntry(
  val descriptor: ChanDescriptor,
  val thumbnailUrl: HttpUrl,
  val title: String
)