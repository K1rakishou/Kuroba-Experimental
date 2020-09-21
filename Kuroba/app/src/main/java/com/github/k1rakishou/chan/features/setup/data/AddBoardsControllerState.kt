package com.github.k1rakishou.chan.features.setup.data

sealed class AddBoardsControllerState {
  object Loading : AddBoardsControllerState()
  object Empty : AddBoardsControllerState()
  data class Error(val errorText: String) : AddBoardsControllerState()
  data class Data(val selectableBoardCellDataList: List<SelectableBoardCellData>) : AddBoardsControllerState()
}