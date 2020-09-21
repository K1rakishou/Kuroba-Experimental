package com.github.k1rakishou.chan.features.setup.data

sealed class BoardSelectionControllerState {
  object Loading : BoardSelectionControllerState()
  object Empty : BoardSelectionControllerState()
  data class Error(val errorText: String) : BoardSelectionControllerState()
  data class Data(val sortedSiteWithBoardsData: Map<SiteCellData, List<BoardCellData>>) : BoardSelectionControllerState()
}