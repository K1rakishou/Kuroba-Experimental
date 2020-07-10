package com.github.adamantcheese.chan.ui.controller.navigation

import android.content.Context
import android.content.res.Configuration
import android.view.View
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.ui.NavigationControllerContainerLayout
import com.github.adamantcheese.chan.features.bookmarks.BookmarksController
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.common.updateMargins
import javax.inject.Inject

class BottomNavBarAwareNavigationController(
  context: Context,
  private val listener: CloseBottomNavBarAwareNavigationControllerListener
) : ToolbarNavigationController(context), OnMeasuredCallback {

  @Inject
  lateinit var themeHelper: ThemeHelper

  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_navigation_bottom_nav_bar_aware)
    container = view.findViewById<View>(R.id.container) as NavigationControllerContainerLayout

    val nav = container as NavigationControllerContainerLayout
    nav.setNavigationController(this)
    nav.setSwipeEnabled(false)

    setToolbar(view.findViewById(R.id.toolbar))
    requireToolbar().setBackgroundColor(themeHelper.theme.primaryColor.color)
    requireToolbar().setCallback(this)
  }

  override fun onShow() {
    super.onShow()

    waitForLayout(container, this)
  }

  override fun onMeasured(view: View?): Boolean {
    if (!isTablet()) {
      return true
    }

    if (getScreenOrientation() != Configuration.ORIENTATION_LANDSCAPE) {
      return true
    }

    val hasBookmarksController = childControllers.any { controller -> controller is BookmarksController }
    if (hasBookmarksController) {
      return true
    }

    val v = view
      ?: return true

    val margin = (v.width * 0.1).toInt()

    view.updateMargins(
      margin,
      margin,
      margin,
      margin,
      null,
      null
    )

    return false
  }

  override fun onMenuOrBackClicked(isArrow: Boolean) {
    super.onMenuOrBackClicked(isArrow)

    listener.onCloseController()
  }

  interface CloseBottomNavBarAwareNavigationControllerListener {
    fun onCloseController()
  }
}