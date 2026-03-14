package com.github.k1rakishou.chan.features.toolbar.state

import androidx.annotation.CallSuper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.features.toolbar.AbstractToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenu
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuCheckableOverflowItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.ui.compose.badge.ToolbarBadgeData
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.core_logger.Logger

abstract class KurobaToolbarSubState {
  abstract val kind: ToolbarStateKind
  abstract val leftMenuItem: ToolbarMenuItem?
  abstract val rightToolbarMenu: ToolbarMenu?

  private val _toolbarBadgeState = mutableStateOf<ToolbarBadgeData?>(null)
  val toolbarBadgeState: State<ToolbarBadgeData?>
    get() = _toolbarBadgeState

  abstract fun update(params: IKurobaToolbarParams)

  @CallSuper
  open fun onCreated() {
    Logger.verbose(tag()) { "onCreated()" }
  }

  @CallSuper
  open fun onShown() {
    Logger.verbose(tag()) { "onShown()" }
  }

  @CallSuper
  open fun onHidden() {
    Logger.verbose(tag()) { "onHidden()" }
  }

  @CallSuper
  open fun onDestroyed() {
    Logger.verbose(tag()) { "onDestroyed()" }
  }

  fun updateBadge(counter: Int, highImportance: Boolean) {
    _toolbarBadgeState.value = ToolbarBadgeData(
      counter = counter,
      highlight = highImportance
    )
  }

  fun hideBadge() {
    _toolbarBadgeState.value = null
  }

  fun findItem(id: Int): ToolbarMenuItem? {
    if (leftMenuItem?.id == id) {
      return leftMenuItem
    }

    val toolbarMenu = rightToolbarMenu
      ?: return null

    for (menuItem in toolbarMenu.menuItems) {
      if (menuItem.id == id) {
        return menuItem
      }
    }

    return null
  }

  fun findOverflowItem(
    id: Int,
    overflowMenuItems: List<AbstractToolbarMenuOverflowItem>? = rightToolbarMenu?.overflowMenuItems?.toList()
  ): ToolbarMenuOverflowItem? {
    if (overflowMenuItems == null) {
      return null
    }

    for (overflowMenuItem in overflowMenuItems) {
      if (overflowMenuItem is ToolbarMenuOverflowItem && overflowMenuItem.id == id) {
        return overflowMenuItem
      }

      val foundItem = findOverflowItem(id, overflowMenuItem.subItems)
      if (foundItem != null) {
        return foundItem
      }
    }

    return null
  }

  fun findCheckableOverflowItem(
    id: Int,
    overflowMenuItems: List<AbstractToolbarMenuOverflowItem>? = rightToolbarMenu?.overflowMenuItems?.toList()
  ): ToolbarMenuCheckableOverflowItem? {
    if (overflowMenuItems == null) {
      return null
    }

    for (overflowMenuItem in overflowMenuItems) {
      if (overflowMenuItem is ToolbarMenuCheckableOverflowItem && overflowMenuItem.id == id) {
        return overflowMenuItem
      }

      val foundItem = findCheckableOverflowItem(id, overflowMenuItem.subItems)
      if (foundItem != null) {
        return foundItem
      }
    }

    return null
  }

  fun checkOrUncheckItem(
    subItem: ToolbarMenuCheckableOverflowItem,
    check: Boolean,
    overflowMenuItems: List<AbstractToolbarMenuOverflowItem>? = rightToolbarMenu?.overflowMenuItems?.toList()
  ) {
    if (overflowMenuItems == null) {
      return
    }

    val groupId = subItem.groupId

    for (overflowMenuItem in overflowMenuItems) {
      if (overflowMenuItem is ToolbarMenuCheckableOverflowItem) {
        if (overflowMenuItem.id == subItem.id) {
          overflowMenuItem.updateChecked(check)
        } else if (groupId != null && overflowMenuItem.groupId == groupId) {
          overflowMenuItem.updateChecked(false)
        }
      }

      checkOrUncheckItem(subItem, check, overflowMenuItem.subItems)
    }
  }

  private fun tag(): String {
    return "KurobaToolbarSubState_${kind}"
  }

}

enum class ToolbarContentState {
  Empty,
  Loading,
  Loaded,
  Error;

  val isLoaded: Boolean
    get() = this == Loaded

  companion object {
    fun from(state: ThreadLayout.State): ToolbarContentState {
      return when (state) {
        ThreadLayout.State.EMPTY -> ToolbarContentState.Empty
        ThreadLayout.State.LOADING -> ToolbarContentState.Loading
        ThreadLayout.State.CONTENT -> ToolbarContentState.Loaded
        ThreadLayout.State.ERROR -> ToolbarContentState.Error
      }
    }
  }

}

enum class ToolbarStateKind {
  Container,
  Catalog,
  Thread,
  ThreadSearch,
  Default,
  Search,
  CatalogSearch,
  Selection,
  Reply;

  fun isDefaultToolbar(): Boolean {
    return when (this) {
      Container,
      Catalog,
      Thread,
      Default -> true
      ThreadSearch,
      Search,
      CatalogSearch,
      Selection,
      Reply -> false
    }
  }

  fun needForceShowToolbar(): Boolean {
    return when (this) {
      Container,
      Catalog,
      Thread,
      Default -> false
      ThreadSearch,
      Search,
      CatalogSearch,
      Selection,
      Reply -> true
    }
  }

  fun needForceHideFab(): Boolean {
    return when (this) {
      Container,
      Catalog,
      Thread,
      Default -> false
      ThreadSearch,
      Search,
      CatalogSearch,
      Selection,
      Reply -> true
    }
  }

  fun needKeyboard(): Boolean {
    return when (this) {
      Search,
      ThreadSearch,
      CatalogSearch -> true
      Selection,
      Reply,
      Container,
      Catalog,
      Thread,
      Default -> false
    }
  }

}