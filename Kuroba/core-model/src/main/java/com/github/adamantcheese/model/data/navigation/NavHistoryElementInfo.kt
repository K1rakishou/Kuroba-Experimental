package com.github.adamantcheese.model.data.navigation

import okhttp3.HttpUrl

data class NavHistoryElementInfo(
  val thumbnailUrl: HttpUrl,
  val title: String
)