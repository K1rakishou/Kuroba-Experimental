package com.github.k1rakishou.chan.features.bookmarks.data

sealed class BookmarksControllerState {
  object Loading : BookmarksControllerState()
  object Empty : BookmarksControllerState()
  data class NothingFound(val searchQuery: String) : BookmarksControllerState()
  data class Error(val errorText: String) : BookmarksControllerState()
  data class Data(val groupedBookmarks: List<GroupOfThreadBookmarkItemViews>) : BookmarksControllerState()
}