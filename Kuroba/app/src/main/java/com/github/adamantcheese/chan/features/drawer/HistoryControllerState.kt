package com.github.adamantcheese.chan.features.drawer

sealed class HistoryControllerState {
  object Loading : HistoryControllerState()
  object Empty : HistoryControllerState()
  data class Error(val errorText: String) : HistoryControllerState()
  data class Data(val navHistoryEntryList: List<NavigationHistoryEntry>) : HistoryControllerState()
}