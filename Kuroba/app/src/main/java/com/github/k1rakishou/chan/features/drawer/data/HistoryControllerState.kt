package com.github.k1rakishou.chan.features.drawer.data

sealed class HistoryControllerState {
  object Loading : HistoryControllerState()
  data class Error(val errorText: String) : HistoryControllerState()
  object Data : HistoryControllerState()
}