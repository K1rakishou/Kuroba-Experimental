package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

abstract class BaseSelectionHelper<T> {
  protected val selectedItems = mutableSetOf<T>()

  private val selectionUpdatesChannel = MutableSharedFlow<SelectionEvent>(extraBufferCapacity = 32)

  fun listenForSelectionChanges(): SharedFlow<SelectionEvent> {
    return selectionUpdatesChannel
      .asSharedFlow()
  }

  open fun toggleSelection(items: Collection<T>) {
    if (items.isEmpty()) {
      return
    }

    val wasInSelectionMode = selectedItems.isNotEmpty()

    val shouldDeselectAll = selectedItems.containsAll(items)
    if (shouldDeselectAll) {
      selectedItems.removeAll(items)
    } else {
      selectedItems.addAll(items)
    }

    val isInSelectionMode = selectedItems.isNotEmpty()
    fireNewSelectionEvent(wasInSelectionMode, isInSelectionMode)
  }

  open fun toggleSelection(item: T) {
    val wasInSelectionMode = selectedItems.isNotEmpty()

    if (selectedItems.contains(item)) {
      selectedItems.remove(item)
    } else {
      selectedItems.add(item)
    }

    val isInSelectionMode = selectedItems.isNotEmpty()
    fireNewSelectionEvent(wasInSelectionMode, isInSelectionMode)
  }

  open fun isInSelectionMode(): Boolean = selectedItems.isNotEmpty()

  open fun isSelected(item: T): Boolean = selectedItems.contains(item)

  open fun selectedItemsCount(): Int = selectedItems.size

  open fun clearSelection() {
    selectedItems.clear()
    onSelectionChanged(SelectionEvent.ExitedSelectionMode)
  }

  private fun fireNewSelectionEvent(
    wasInSelectionMode: Boolean,
    isInSelectionMode: Boolean
  ) {
    when {
      !wasInSelectionMode && isInSelectionMode -> {
        onSelectionChanged(SelectionEvent.EnteredSelectionMode)
      }
      wasInSelectionMode && !isInSelectionMode -> {
        onSelectionChanged(SelectionEvent.ExitedSelectionMode)
      }
      else -> {
        onSelectionChanged(SelectionEvent.ItemSelectionToggled)
      }
    }
  }

  private fun onSelectionChanged(selectionEvent: SelectionEvent) {
    selectionUpdatesChannel.tryEmit(selectionEvent)
  }

  sealed class SelectionEvent {
    fun isIsSelectionMode(): Boolean {
      return this is EnteredSelectionMode || this is ItemSelectionToggled
    }

    object EnteredSelectionMode : SelectionEvent()
    object ItemSelectionToggled : SelectionEvent()
    object ExitedSelectionMode : SelectionEvent()
  }

}