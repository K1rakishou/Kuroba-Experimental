package com.github.k1rakishou.chan.features.setup.site.settings

import com.github.k1rakishou.chan.ui.controller.base.Controller

interface SiteSettingsView {
  suspend fun showErrorToast(message: String)
  fun pushController(controller: Controller)
}