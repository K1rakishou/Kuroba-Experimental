package com.github.k1rakishou.chan.features.setup.data

sealed class BoardSelectionControllerState {
  object Empty : BoardSelectionControllerState()
  data class Error(val errorText: String) : BoardSelectionControllerState()

  data class Data(
    val isGridMode: Boolean,
    val sortedSiteWithBoardsData: Map<SiteCellData, List<BoardCellData>>
  ) : BoardSelectionControllerState()
}