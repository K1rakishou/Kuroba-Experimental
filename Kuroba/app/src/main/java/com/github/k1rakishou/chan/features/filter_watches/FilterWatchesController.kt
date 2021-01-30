package com.github.k1rakishou.chan.features.filter_watches

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.navigation.TabPageController
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils

class FilterWatchesController(
  context: Context,
) : TabPageController(context) {

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_filter_watches)
  }

  override fun rebuildNavigationItem(navigationItem: NavigationItem) {
    navigationItem.title = AppModuleAndroidUtils.getString(R.string.controller_filter_watches)
    navigationItem.swipeable = false
  }

  override fun onTabFocused() {

  }

  override fun canSwitchTabs(): Boolean {
    return true
  }
}