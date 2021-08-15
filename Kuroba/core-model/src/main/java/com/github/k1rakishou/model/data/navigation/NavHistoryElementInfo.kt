package com.github.k1rakishou.model.data.navigation

import okhttp3.HttpUrl

data class NavHistoryElementInfo(
  val thumbnailUrl: HttpUrl,
  val title: String,
  var pinned: Boolean
)