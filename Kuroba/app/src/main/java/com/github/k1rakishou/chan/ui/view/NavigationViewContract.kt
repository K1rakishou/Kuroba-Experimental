package com.github.k1rakishou.chan.ui.view

import android.view.ViewGroup
import com.github.k1rakishou.chan.ui.compose.bottom_panel.KurobaComposeIconPanel
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.core_themes.ChanTheme

interface NavigationViewContract {
  var viewElevation: Float
  var selectedMenuItemId: Int

  val actualView: ViewGroup
  val type: Type

  fun setOnNavigationItemSelectedListener(listener: (Int) -> Boolean)

  fun updatePaddings(leftPadding: Int?, bottomPadding: Int?)
  fun setToolbar(toolbar: Toolbar)
  fun hide(lockTranslation: Boolean, lockCollapse: Boolean)
  fun show(unlockTranslation: Boolean, unlockCollapse: Boolean)
  fun resetState(unlockTranslation: Boolean, unlockCollapse: Boolean)

  fun setMenuItemSelected(menuItemId: Int)
  fun updateBadge(menuItemId: Int, menuItemBadgeInfo: KurobaComposeIconPanel.MenuItemBadgeInfo?)
  fun onThemeChanged(chanTheme: ChanTheme)

  enum class Type {
    BottomNavView,
    SideNavView
  }

}