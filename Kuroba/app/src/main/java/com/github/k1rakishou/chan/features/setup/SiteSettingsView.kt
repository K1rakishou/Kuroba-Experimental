package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.controller.Controller

interface SiteSettingsView {
  suspend fun showErrorToast(message: String)
  fun pushController(controller: Controller)
  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller)
}