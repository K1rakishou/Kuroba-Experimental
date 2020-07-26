package com.github.adamantcheese.chan.features.drawer

import android.view.MotionEvent

interface DrawerCallbacks {
  fun resetBottomNavViewCheckState()
  fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean)
  fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean)
  fun resetBottomNavViewState(unlockTranslation: Boolean, unlockCollapse: Boolean)
  fun passMotionEventIntoDrawer(event: MotionEvent): Boolean
}