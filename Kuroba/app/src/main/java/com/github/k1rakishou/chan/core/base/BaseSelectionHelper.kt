package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

abstract class BaseSelectionHelper<T> {
  protected val selectedItems = mutableListOf<T>()

  private val selectionUpdatesChannel = ConflatedBroadcastChannel<Unit>()

  fun listenForSelectionChanges(): Flow<Unit> {
    return selectionUpdatesChannel
      .asFlow()
  }

  open fun toggleSelection(item: T) {
    if (selectedItems.contains(item)) {
      selectedItems.remove(item)
    } else {
      selectedItems.add(item)
    }

    onSelectionChanged()
  }

  open fun isSelected(item: T): Boolean = selectedItems.contains(item)

  open fun selectedItemsCount(): Int = selectedItems.size

  open fun clearSelection(notifyListeners: Boolean = true) {
    selectedItems.clear()

    if (notifyListeners) {
      onSelectionChanged()
    }
  }

  private fun onSelectionChanged() {
    selectionUpdatesChannel.offer(Unit)
  }

}