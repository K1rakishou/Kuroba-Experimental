package com.github.adamantcheese.chan.features.setup

import com.github.adamantcheese.chan.controller.Controller

interface SiteSettingsView {
  suspend fun showErrorToast(message: String)
  fun pushController(controller: Controller)
  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller)
}