package com.github.k1rakishou.chan.ui.view.bottom_menu_panel

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class BottomMenuPanelItem(
  val menuItemId: BottomMenuPanelItemId,
  @DrawableRes val iconResId: Int,
  @StringRes val textResId: Int,
  val onClickListener: (BottomMenuPanelItemId) -> Unit
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BottomMenuPanelItem

    if (menuItemId != other.menuItemId) return false

    return true
  }

  override fun hashCode(): Int {
    return menuItemId.hashCode()
  }

}