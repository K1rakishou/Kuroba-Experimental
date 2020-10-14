package com.github.k1rakishou.chan.core.base

import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor

abstract class BaseSelectionHelper<T> {
  protected val selectedItems = mutableListOf<T>()

  val selectionUpdatesSubject = PublishProcessor.create<Unit>()

  fun listenForSelectionChanges(): Flowable<Unit> {
    return selectionUpdatesSubject
      .onBackpressureLatest()
      .hide()
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
    selectionUpdatesSubject.onNext(Unit)
  }

}