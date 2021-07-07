package com.github.k1rakishou.chan.core.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ViewModelSelectionHelper<Element, MenuItemClickEvent> {
  private var _selectionMode = MutableCachedSharedFlow<BaseSelectionHelper.SelectionEvent?>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val selectionMode: SharedFlow<BaseSelectionHelper.SelectionEvent?>
    get() = _selectionMode.sharedFlow

  private val _bottomPanelMenuItemClickEventFlow = MutableSharedFlow<MenuItemClickEvent>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val bottomPanelMenuItemClickEventFlow: SharedFlow<MenuItemClickEvent>
    get() = _bottomPanelMenuItemClickEventFlow.asSharedFlow()

  private val selectedItems = mutableMapOf<Element, MutableState<Boolean>>()

  @Composable
  fun collectSelectionModeAsState(): State<BaseSelectionHelper.SelectionEvent?> {
    return _selectionMode.collectAsState()
  }

  fun emitBottomPanelMenuItemClickEvent(clickEvent: MenuItemClickEvent) {
    _bottomPanelMenuItemClickEventFlow.tryEmit(clickEvent)
  }

  fun selectedItemsCount(): Int {
    return selectedItems.count { (_, selectionState) -> selectionState.value }
  }

  fun isInSelectionMode(): Boolean {
    if (selectedItems.isEmpty()) {
      return false
    }

    return selectedItems.any { (_, selectionState) -> selectionState.value }
  }

  fun isSelected(element: Element): Boolean {
    return selectedItems[element]?.value == true
  }

  fun select(element: Element) {
    if (!selectedItems.containsKey(element)) {
      selectedItems[element] = mutableStateOf(true)
    } else {
      selectedItems[element]?.value = true
    }
    updateSelectionModeFlag()
  }

  fun unselect(element: Element) {
    selectedItems[element]?.value = false
    updateSelectionModeFlag()
  }

  fun toggleSelection(element: Element) {
    if (!selectedItems.containsKey(element)) {
      selectedItems.put(element, mutableStateOf(false))
    }

    var selectionState by selectedItems[element]!!
    selectionState = selectionState.not()

    updateSelectionModeFlag()
  }

  fun observeSelectionState(element: Element): State<Boolean> {
    if (!selectedItems.containsKey(element)) {
      selectedItems.put(element, mutableStateOf(false))
      updateSelectionModeFlag()
    }

    return selectedItems[element]!!
  }

  fun unselectAll(): Boolean {
    if (selectedItems.isEmpty()) {
      return false
    }

    selectedItems.clear()
    updateSelectionModeFlag()
    return true
  }

  fun getCurrentlySelectedItems(): List<Element> {
    return selectedItems
      .filter { (_, selectedItem) -> selectedItem.value }
      .map { (element, _) -> element }
  }

  private fun updateSelectionModeFlag() {
    val wasInSelectionMode = _selectionMode.cachedValue?.isIsSelectionMode() ?: false
    val nowInSelectionMode = isInSelectionMode()

    val newValue = when {
      wasInSelectionMode && nowInSelectionMode -> BaseSelectionHelper.SelectionEvent.ItemSelectionToggled
      nowInSelectionMode -> BaseSelectionHelper.SelectionEvent.EnteredSelectionMode
      else ->  BaseSelectionHelper.SelectionEvent.ExitedSelectionMode
    }

    _selectionMode.tryEmit(newValue)
  }

}