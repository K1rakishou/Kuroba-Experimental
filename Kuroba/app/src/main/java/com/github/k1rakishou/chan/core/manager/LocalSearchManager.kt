package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy

class LocalSearchManager {
  @GuardedBy("this")
  private val searchQueryMap = mutableMapOf<LocalSearchType, String>()

  @Synchronized
  fun onSearchEntered(localSearchType: LocalSearchType, query: String?) {
    if (query == null) {
      clearSearch(localSearchType)
      return
    }

    searchQueryMap[localSearchType] = query
  }

  @Synchronized
  fun clearSearch(localSearchType: LocalSearchType) {
    searchQueryMap.remove(localSearchType)
  }

  @Synchronized
  fun getSearchQuery(localSearchType: LocalSearchType): String? {
    return searchQueryMap[localSearchType]
  }

  @Synchronized
  fun isSearchOpened(localSearchType: LocalSearchType): Boolean {
    return searchQueryMap.containsKey(localSearchType)
  }
}

enum class LocalSearchType {
  CatalogSearch,
  ThreadSearch
}