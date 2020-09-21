package com.github.k1rakishou.chan

import com.github.k1rakishou.chan.controller.Controller

interface StartActivityCallbacks {
  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller)
  fun setSettingsMenuItemSelected()
  fun setBookmarksMenuItemSelected()
}