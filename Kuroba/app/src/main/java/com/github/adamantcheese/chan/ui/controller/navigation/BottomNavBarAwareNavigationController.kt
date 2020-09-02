package com.github.adamantcheese.chan.ui.controller.navigation

import android.content.Context
import android.content.res.Configuration
import android.view.View
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.ui.NavigationControllerContainerLayout
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager
import com.github.adamantcheese.chan.core.manager.WindowInsetsListener
import com.github.adamantcheese.chan.features.bookmarks.BookmarksController
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.common.updateMargins
import com.github.adamantcheese.common.updatePaddings
import javax.inject.Inject

class BottomNavBarAwareNavigationController(
  context: Context,
  private val listener: CloseBottomNavBarAwareNavigationControllerListener
) : ToolbarNavigationController(context), OnMeasuredCallback, WindowInsetsListener {

  @Inject
  lateinit var themeHelper: ThemeHelper
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private var bottomNavBarHeight: Int = 0

  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_navigation_bottom_nav_bar_aware)
    container = view.findViewById<View>(R.id.container) as NavigationControllerContainerLayout

    bottomNavBarHeight = getDimen(R.dimen.bottom_nav_view_height)

    setToolbar(view.findViewById(R.id.toolbar))
    requireToolbar().setBackgroundColor(themeHelper.theme.primaryColor.color)
    requireToolbar().setCallback(this)

    // Wait a little bit so that GlobalWindowInsetsManager have time to get initialized so we can
    // use the insets
    view.post {
      container.updatePaddings(
        bottom = bottomNavBarHeight + globalWindowInsetsManager.bottom()
      )

      globalWindowInsetsManager.addInsetsUpdatesListener(this)
    }
  }

  override fun onShow() {
    super.onShow()

    waitForLayout(container, this)
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    container.updatePaddings(
      bottom = bottomNavBarHeight + globalWindowInsetsManager.bottom()
    )
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
    listener.onCloseController()
  }

  interface CloseBottomNavBarAwareNavigationControllerListener {
    fun onCloseController()
  }
}