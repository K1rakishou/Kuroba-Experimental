package com.github.k1rakishou.chan.features.search

import okhttp3.HttpUrl

interface SearchResultsView {
  fun onCloudFlareDetected(requestUrl: HttpUrl)
}