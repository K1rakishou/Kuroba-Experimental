package com.github.adamantcheese.chan.features.setup.data

sealed class SitesSetupControllerState {
  object Loading : SitesSetupControllerState()
  object Empty : SitesSetupControllerState()
  data class Error(val errorText: String) : SitesSetupControllerState()
  data class Data(val siteCellDataList: List<SiteCellData>) : SitesSetupControllerState()
}