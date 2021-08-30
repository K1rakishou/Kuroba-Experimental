package com.github.k1rakishou.chan.features.setup.data

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

sealed class BoardSelectionControllerState {
  object Empty : BoardSelectionControllerState()
  data class Error(val errorText: String) : BoardSelectionControllerState()

  data class Data(
    val isGridMode: Boolean,
    val sortedSiteWithBoardsData: Map<SiteCellData, List<BoardCellData>>,
    val currentlySelected: ChanDescriptor.ICatalogDescriptor?
  ) : BoardSelectionControllerState()
}