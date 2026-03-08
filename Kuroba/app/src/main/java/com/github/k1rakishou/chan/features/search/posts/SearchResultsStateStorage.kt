package com.github.k1rakishou.chan.features.search.posts

import com.github.k1rakishou.chan.features.search.posts.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.posts.data.SearchResultsControllerStateData
import com.github.k1rakishou.persist_state.IndexAndTop

internal object SearchResultsStateStorage {
  @get:Synchronized
  @set:Synchronized
  var searchResultsState: SearchResultsControllerStateData? = null
    private set

  @get:Synchronized
  @set:Synchronized
  var lastRecyclerViewScrollState: IndexAndTop? = null
    private set

  @get:Synchronized
  @set:Synchronized
  var searchInputState: GlobalSearchControllerStateData? = null
    private set

  fun updateSearchResultsState(searchResultsState: SearchResultsControllerStateData) {
    this.searchResultsState = searchResultsState
  }

  fun updateLastRecyclerViewScrollState(indexAndTop: IndexAndTop) {
    if (lastRecyclerViewScrollState == null) {
      lastRecyclerViewScrollState = IndexAndTop()
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