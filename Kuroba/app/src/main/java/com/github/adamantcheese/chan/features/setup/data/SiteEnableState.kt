package com.github.adamantcheese.chan.features.setup.data

enum class SiteEnableState {
  Active,
  NotActive,
  // Disabled via the code, the user cannot enable such site (this usually true when the site is dead
  // or broken)
  Disabled;

  companion object {
    fun create(active: Boolean, enabled: Boolean): SiteEnableState {
      if (!enabled) {
        return Disabled
      }

      if (active) {
        return Active
      }

      return NotActive
    }
  }
}