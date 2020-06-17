package com.github.adamantcheese.chan.features.bookmarks.data

sealed class BookmarksControllerState {
  object Loading : BookmarksControllerState()
  object Empty : BookmarksControllerState()
  data class Error(val errorText: String) : BookmarksControllerState()
  data class Data(val bookmarks: List<ThreadBookmarkItemView>) : BookmarksControllerState()
}