package com.github.adamantcheese.chan.ui.controller.navigation

import android.content.Context
import android.view.View
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.ui.NavigationControllerContainerLayout
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.AndroidUtils
import javax.inject.Inject

class BottomNavBarAwareNavigationController(
  context: Context,
  private val listener: CloseBottomNavBarAwareNavigationControllerListener
) : ToolbarNavigationController(context) {

  @Inject
  lateinit var themeHelper: ThemeHelper

  override fun onCreate() {
    super.onCreate()

    view = AndroidUtils.inflate(context, R.layout.controller_navigation_bottom_nav_bar_aware)
    container = view.findViewById<View>(R.id.container) as NavigationControllerContainerLayout

    val nav = container as NavigationControllerContainerLayout
    nav.setNavigationController(this)
    nav.setSwipeEnabled(false)

    setToolbar(view.findViewById(R.id.toolbar))
    requireToolbar().setBackgroundColor(themeHelper.theme.primaryColor.color)
    requireToolbar().setCallback(this)
  }

  override fun onMenuOrBackClicked(isArrow: Boolean) {
    super.onMenuOrBackClicked(isArrow)

    listener.onCloseController()
  }

  interface CloseBottomNavBarAwareNavigationControllerListener {
    fun onCloseController()
  }
}