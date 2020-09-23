package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerStateData
import com.github.k1rakishou.chan.utils.RecyclerUtils

internal object SearchResultsStateStorage {
  @get:Synchronized
  @set:Synchronized
  var searchResultsState: SearchResultsControllerStateData? = null
    private set

  @get:Synchronized
  @set:Synchronized
  var lastRecyclerViewScrollState: RecyclerUtils.IndexAndTop? = null
    private set

  @get:Synchronized
  @set:Synchronized
  var searchInputState: GlobalSearchControllerStateData? = null
    private set

  fun updateSearchResultsState(searchResultsState: SearchResultsControllerStateData) {
    this.searchResultsState = searchResultsState
  }

  fun updateLastRecyclerViewScrollState(indexAndTop: IntArray) {
    if (lastRecyclerViewScrollState == null) {
      lastRecyclerViewScrollState = RecyclerUtils.IndexAndTop()
    }

    lastRecyclerViewScrollState!!.index = indexAndTop[0]
    lastRecyclerViewScrollState!!.top = indexAndTop[1]
  }

  fun updateSearchInputState(searchInputState: GlobalSearchControllerStateData) {
    this.searchInputState = searchInputState
  }

  fun resetSearchInputState() {
    searchInputState = null
  }

  fun resetSearchResultState() {
    searchResultsState = null
    lastRecyclerViewScrollState = null
  }

  fun resetLastRecyclerViewScrollState() {
    lastRecyclerViewScrollState = null
  }
}