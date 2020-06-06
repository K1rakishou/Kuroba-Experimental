package com.github.adamantcheese.chan.features.drawer

interface DrawerCallbacks {
  fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean)
  fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean)
}