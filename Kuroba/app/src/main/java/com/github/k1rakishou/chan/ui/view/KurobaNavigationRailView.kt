package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.updatePadding
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.bottom_panel.KurobaComposeIconPanel
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.core_themes.ChanTheme

class KurobaNavigationRailView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), Toolbar.ToolbarCollapseCallback, NavigationViewContract {

  private val kurobaComposeIconPanel by lazy {
    KurobaComposeIconPanel(
      context = context,
      orientation = KurobaComposeIconPanel.Orientation.Vertical,
      defaultSelectedMenuItemId = R.id.action_browse,
      menuItems = KurobaBottomNavigationView.bottomNavViewButtons()
    )
  }

  private var menuItemClickListener: ((Int) -> Boolean)? = null

  override val actualView: ViewGroup
    get() = this
  override val type: NavigationViewContract.Type
    get() = NavigationViewContract.Type.SideNavView

  override var viewElevation: Float
    get() = elevation
    set(value) { elevation = value }
  override var selectedMenuItemId: Int
    get() = kurobaComposeIconPanel.selectedMenuItemId
    set(value) { kurobaComposeIconPanel.setMenuItemSelected(value) }

  init {
    removeAllViews()
    addView(ComposeView(context).also { composeView -> composeView.setContent { BuildContent() } })
  }

  @Composable
  private fun BuildContent() {
    kurobaComposeIconPanel.BuildPanel(
      onMenuItemClicked = { menuItemId -> menuItemClickListener?.invoke(menuItemId) }
    )
  }

  override fun setMenuItemSelected(menuItemId: Int) {
    kurobaComposeIconPanel.setMenuItemSelected(menuItemId)
  }

  override fun updateBadge(menuItemId: Int, menuItemBadgeInfo: KurobaComposeIconPanel.MenuItemBadgeInfo?) {
    kurobaComposeIconPanel.updateBadge(menuItemId, menuItemBadgeInfo)
  }

  override fun onThemeChanged(chanTheme: ChanTheme) {
    this.setBackgroundColor(chanTheme.primaryColor)
  }

  override fun updatePaddings(leftPadding: Int?, bottomPadding: Int?) {
    leftPadding?.let { padding -> updatePadding(left = padding) }
  }

  override fun onCollapseTranslation(offset: Float) {
    // no-op
  }

  override fun onCollapseAnimation(collapse: Boolean) {
    // no-op
  }

  override fun setOnNavigationItemSelectedListener(listener: (Int) -> Boolean) {
    this.menuItemClickListener = listener
  }

  override fun setOnOuterInterceptTouchEventListener(listener: (MotionEvent) -> Boolean) {
    // no-op
  }

  override fun setOnOuterTouchEventListener(listener: (MotionEvent) -> Boolean) {
    // no-op
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