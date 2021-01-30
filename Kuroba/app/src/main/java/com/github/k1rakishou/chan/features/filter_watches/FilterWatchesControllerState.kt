package com.github.k1rakishou.chan.features.filter_watches

sealed class FilterWatchesControllerState {
  object Loading : FilterWatchesControllerState()
  object Empty : FilterWatchesControllerState()
  data class Error(val errorText: String) : FilterWatchesControllerState()

  data class Data(
    val groupedFilterWatches: List<GroupOfFilterWatches>
  ) : FilterWatchesControllerState()
}