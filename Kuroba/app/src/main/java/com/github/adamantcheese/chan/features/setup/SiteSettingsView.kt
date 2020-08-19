package com.github.adamantcheese.chan.features.setup

interface SiteSettingsView {
  suspend fun showErrorToast(message: String)
}