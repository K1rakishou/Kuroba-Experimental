package com.github.k1rakishou.chan.ui.view

import android.content.res.ColorStateList
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.forEach
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.google.android.material.badge.BadgeDrawable

interface NavigationViewContract {
  var viewItemTextColor: ColorStateList?
  var viewItemIconTintList: ColorStateList?

  var viewElevation: Float
  var selectedMenuItemId: Int

  val actualView: ViewGroup
  val navigationMenu: Menu
  val type: Type

  fun setOnNavigationItemSelectedListener(listener: (MenuItem) -> Boolean)
  fun setOnOuterInterceptTouchEventListener(listener: (MotionEvent) -> Boolean)
  fun setOnOuterTouchEventListener(listener: (MotionEvent) -> Boolean)
  fun setBackgroundColor(color: Int)
  fun updatePaddings(leftPadding: Int?, bottomPadding: Int?)
  fun setToolbar(toolbar: Toolbar)
  fun hide(lockTranslation: Boolean, lockCollapse: Boolean)
  fun show(unlockTranslation: Boolean, unlockCollapse: Boolean)
  fun resetState(unlockTranslation: Boolean, unlockCollapse: Boolean)
  fun getBadge(menuItemId: Int): BadgeDrawable?
  fun removeBadge(menuItemId: Int)
  fun getOrCreateBadge(menuItemId: Int): BadgeDrawable

  fun updateMenuItem(menuItemId: Int, updater: MenuItem.() -> Unit) {
    navigationMenu.findItem(menuItemId)?.let { menuItem ->
      updater(menuItem)
    }

    disableTooltips()
  }

  fun disableTooltips() {
    navigationMenu.forEach { menuItem ->
      val view = actualView.findViewById<View>(menuItem.itemId)
      TooltipCompat.setTooltipText(view, null)
    }
  }

  enum class Type {
    BottomNavView,
    SideNavView
  }

}