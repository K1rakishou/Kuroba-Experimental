package com.github.k1rakishou.chan.features.drawer

import android.view.MotionEvent
import com.github.k1rakishou.chan.ui.view.NavigationViewContract
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem

interface MainControllerCallbacks {
  val isBottomPanelShown: Boolean
  val bottomPanelHeight: Int
  val navigationViewContractType: NavigationViewContract.Type

  fun resetBottomNavViewCheckState()
  fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean)
  fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean)
  fun resetBottomNavViewState(unlockTranslation: Boolean, unlockCollapse: Boolean)
  fun passMotionEventIntoDrawer(event: MotionEvent): Boolean

  fun onBottomPanelStateChanged(func: (BottomMenuPanel.State) -> Unit)
  fun showBottomPanel(items: List<BottomMenuPanelItem>)
  fun hideBottomPanel()
  fun passOnBackToBottomPanel(): Boolean
}