package com.github.k1rakishou.chan.features.setup.data

sealed class BoardsSetupControllerState {
  object Loading : BoardsSetupControllerState()
  object Empty : BoardsSetupControllerState()
  data class Error(val errorText: String) : BoardsSetupControllerState()
  data class Data(val catalogCellDataList: List<CatalogCellData>) : BoardsSetupControllerState()
}