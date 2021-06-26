package com.github.k1rakishou.chan.features.bookmarks

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItemId
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class BookmarksSelectionHelper(
  private val bookmarkMenuItemClickListener: OnBookmarkMenuItemClicked
) : BaseSelectionHelper<ChanDescriptor.ThreadDescriptor>() {

  fun getBottomPanelMenus(): List<BottomMenuPanelItem> {
    if (selectedItems.isEmpty()) {
      return emptyList()
    }

    val itemsList = mutableListOf<BottomMenuPanelItem>()

    itemsList += BottomMenuPanelItem(
      BookmarksMenuItemId(BookmarksMenuItemType.Delete),
      R.drawable.ic_baseline_delete_outline_24,
      R.string.bottom_menu_item_delete,
      { bookmarkMenuItemClickListener.onMenuItemClicked(BookmarksMenuItemType.Delete, selectedItems.toList()) }
    )

    itemsList += BottomMenuPanelItem(
      BookmarksMenuItemId(BookmarksMenuItemType.Delete),
      R.drawable.ic_reorder_white_24dp,
      R.string.bottom_menu_item_reorder,
      { bookmarkMenuItemClickListener.onMenuItemClicked(BookmarksMenuItemType.Reorder, selectedItems.toList()) }
    )

    return itemsList
  }

  enum class BookmarksMenuItemType(val id: Int) {
    Delete(0),
    Reorder(1)
  }

  class BookmarksMenuItemId(val bookmarksMenuItemType: BookmarksMenuItemType) : BottomMenuPanelItemId {
    override fun id(): Int {
      return bookmarksMenuItemType.id
    }
  }

  interface OnBookmarkMenuItemClicked {
    fun onMenuItemClicked(
      bookmarksMenuItemType: BookmarksMenuItemType,
      selectedItems: List<ChanDescriptor.ThreadDescriptor>
    )
  }

}