package com.github.k1rakishou.chan.features.proxies

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItemId

class ProxySelectionHelper(
  private val proxyMenuItemClickListener: OnProxyItemClicked
) : BaseSelectionHelper<ProxyStorage.ProxyKey>() {

  fun getBottomPanelMenus(): List<BottomMenuPanelItem> {
    if (selectedItems.isEmpty()) {
      return emptyList()
    }

    val itemsList = mutableListOf<BottomMenuPanelItem>()

    itemsList += BottomMenuPanelItem(
      BookmarksMenuItemId(ProxyMenuItemType.Delete),
      R.drawable.ic_baseline_delete_outline_24,
      R.string.bottom_menu_item_delete,
      { proxyMenuItemClickListener.onMenuItemClicked(ProxyMenuItemType.Delete, selectedItems.toList()) }
    )

    return itemsList
  }

  enum class ProxyMenuItemType(val id: Int) {
    Delete(0)
  }

  class BookmarksMenuItemId(val proxyMenuItemType: ProxyMenuItemType) :
    BottomMenuPanelItemId {
    override fun id(): Int {
      return proxyMenuItemType.id
    }
  }

  interface OnProxyItemClicked {
    fun onMenuItemClicked(
      proxyMenuItemType: ProxyMenuItemType,
      selectedItems: List<ProxyStorage.ProxyKey>
    )
  }

}