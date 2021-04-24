package com.github.k1rakishou.chan.features.drawer.data

sealed class HistoryControllerState {
  object Loading : HistoryControllerState()
  object Empty : HistoryControllerState()
  data class Error(val errorText: String) : HistoryControllerState()

  data class Data(
    val isGridLayoutMode: Boolean,
    val navHistoryEntryList: List<NavigationHistoryEntry>
  ) : HistoryControllerState()
}