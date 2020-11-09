package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.PersistableChanState
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerStateData

internal object SearchResultsStateStorage {
  @get:Synchronized
  @set:Synchronized
  var searchResultsState: SearchResultsControllerStateData? = null
    private set

  @get:Synchronized
  @set:Synchronized
  var lastRecyclerViewScrollState: PersistableChanState.IndexAndTop? = null
    private set

  @get:Synchronized
  @set:Synchronized
  var searchInputState: GlobalSearchControllerStateData? = null
    private set

  fun updateSearchResultsState(searchResultsState: SearchResultsControllerStateData) {
    this.searchResultsState = searchResultsState
  }

  fun updateLastRecyclerViewScrollState(indexAndTop: PersistableChanState.IndexAndTop) {
    if (lastRecyclerViewScrollState == null) {
      lastRecyclerViewScrollState = PersistableChanState.IndexAndTop()
    }

    lastRecyclerViewScrollState!!.index = indexAndTop.index
    lastRecyclerViewScrollState!!.top = indexAndTop.top
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