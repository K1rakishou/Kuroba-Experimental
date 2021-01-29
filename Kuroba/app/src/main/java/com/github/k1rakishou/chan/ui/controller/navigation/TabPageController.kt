package com.github.k1rakishou.chan.ui.controller.navigation

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem

abstract class TabPageController(
  context: Context,
) : Controller(context) {
  abstract fun rebuildNavigationItem(navigationItem: NavigationItem)
  abstract fun canSwitchTabs(): Boolean
}