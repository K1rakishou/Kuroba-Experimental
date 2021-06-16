package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener
import com.google.android.material.navigationrail.NavigationRailView

class KurobaNavigationRailView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : NavigationRailView(context, attrs, defStyleAttr), Toolbar.ToolbarCollapseCallback, NavigationViewContract {

  override val actualView: ViewGroup
    get() = this
  override val navigationMenu: Menu
    get() = menu
  override val type: NavigationViewContract.Type
    get() = NavigationViewContract.Type.SideNavView

  override var viewItemTextColor: ColorStateList?
    get() = itemTextColor
    set(value) { itemTextColor = value }
  override var viewItemIconTintList: ColorStateList?
    get() = itemIconTintList
    set(value) { itemIconTintList = value }
  override var viewElevation: Float
    get() = elevation
    set(value) { elevation = value }
  override var selectedMenuItemId: Int
    get() = selectedItemId
    set(value) { selectedItemId = value }

  init {
    labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_UNLABELED
  }

  override fun onCollapseTranslation(offset: Float) {
    // no-op
  }

  override fun onCollapseAnimation(collapse: Boolean) {
    // no-op
  }

  override fun setOnNavigationItemSelectedListener(listener: (MenuItem) -> Boolean) {
    this.setOnItemSelectedListener(OnItemSelectedListener { item -> listener(item) })
  }

  override fun setOnOuterInterceptTouchEventListener(listener: (MotionEvent) -> Boolean) {
    // no-op
  }

  override fun setOnOuterTouchEventListener(listener: (MotionEvent) -> Boolean) {
    // no-op
  }

  override fun updatePaddings(leftPadding: Int?, bottomPadding: Int?) {
    updatePadding(left = leftPadding!!)
  }

  override fun setToolbar(toolbar: Toolbar) {
    // no-op
  }

  override fun hide(lockTranslation: Boolean, lockCollapse: Boolean) {
    // no-op
  }

  override fun show(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    // no-op
  }

  override fun resetState(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    // no-op
  }
}