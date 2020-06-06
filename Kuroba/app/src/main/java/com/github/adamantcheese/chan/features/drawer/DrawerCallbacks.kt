package com.github.adamantcheese.chan.features.drawer

interface DrawerCallbacks {
  fun resetBottomNavViewCheckState()
  fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean)
  fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean)
}