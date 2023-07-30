package com.github.k1rakishou.chan.ui.view

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.github.k1rakishou.BottomNavViewButton
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.bottom_panel.KurobaComposeIconPanel
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.Toolbar.ToolbarCollapseCallback
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.persist_state.PersistableChanState

class KurobaBottomNavigationView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ToolbarCollapseCallback, NavigationViewContract {
  private var attachedToWindow = false
  private var toolbar: Toolbar? = null
  private var attachedToToolbar = false

  private var animating = false
  private var lastCollapseTranslationOffset = 0f
  private var isTranslationLocked = false
  private var isCollapseLocked = false
  private var isBottomNavViewEnabled = ChanSettings.isNavigationViewEnabled()

  private var interceptTouchEventListener: ((MotionEvent) -> Boolean)? = null
  private var touchEventListener: ((MotionEvent) -> Boolean)? = null
  private var menuItemClickListener: ((Int) -> Boolean)? = null

  private val kurobaComposeIconPanel by lazy {
    KurobaComposeIconPanel(
      context = context,
      orientation = KurobaComposeIconPanel.Orientation.Horizontal,
      defaultSelectedMenuItemId = R.id.action_browse,
      menuItems = bottomNavViewButtons()
    )
  }

  override val actualView: ViewGroup
    get() = this
  override val type: NavigationViewContract.Type
    get() = NavigationViewContract.Type.BottomNavView

  override var viewElevation: Float
    get() = elevation
    set(value) { elevation = value }
  override var selectedMenuItemId: Int
    get() = kurobaComposeIconPanel.selectedMenuItemId
    set(value) { kurobaComposeIconPanel.setMenuItemSelected(value) }

  init {
    setOnApplyWindowInsetsListener(null)

    if (!isBottomNavViewEnabled) {
      completelyDisableBottomNavigationView()
    } else {
      removeAllViews()
      addView(ComposeView(context).also { composeView -> composeView.setContent { BuildContent() } })
    }
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

  override fun updatePaddings(leftPadding: Int?, bottomPadding: Int?) {
    bottomPadding?.let { padding -> updatePaddings(bottom = padding) }
  }

  override fun onThemeChanged(chanTheme: ChanTheme) {
    this.setBackgroundColor(chanTheme.primaryColor)
  }

  override fun setToolbar(toolbar: Toolbar) {
    if (!isBottomNavViewEnabled) {
      completelyDisableBottomNavigationView()
      return
    }

    this.toolbar = toolbar

    if (attachedToWindow && !attachedToToolbar) {
      toolbar.addCollapseCallback(this)
      attachedToToolbar = true
    }
  }

  override fun hide(lockTranslation: Boolean, lockCollapse: Boolean) {
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      throw IllegalStateException("The nav bar should always be visible when using SPLIT layout")
    }

    if (!isBottomNavViewEnabled) {
      completelyDisableBottomNavigationView()
      return
    }

    onCollapseAnimationInternal(collapse = true, isFromToolbarCallbacks = false)

    if (lockTranslation) {
      isTranslationLocked = true
    }

    if (lockCollapse) {
      isCollapseLocked = true
    }
  }

  override fun show(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
      throw IllegalStateException("The nav bar should always be visible when using SPLIT layout")
    }

    if (!isBottomNavViewEnabled) {
      completelyDisableBottomNavigationView()
      return
    }

    if (unlockTranslation) {
      isTranslationLocked = false
    }

    if (unlockCollapse) {
      isCollapseLocked = false
    }

    onCollapseAnimationInternal(collapse = false, isFromToolbarCallbacks = false)
  }

  override fun resetState(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    if (!isBottomNavViewEnabled) {
      completelyDisableBottomNavigationView()
      return
    }

    isTranslationLocked = !unlockTranslation
    isCollapseLocked = !unlockCollapse

    restoreHeightWithAnimation()
  }

  fun isFullyVisible(): Boolean {
    return alpha >= 0.99f && isBottomNavViewEnabled
  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
    if (!isFullyVisible()) {
      // Steal event from children
      return true
    }

    if (ev != null) {
      val result = interceptTouchEventListener?.invoke(ev)
      if (result == true) {
        return true
      }
    }

    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    if (!isFullyVisible()) {
      // Pass event to other views
      return false
    }

    if (event != null) {
      val result = touchEventListener?.invoke(event)
      if (result == true) {
        return true
      }
    }

    return super.onTouchEvent(event)
  }

  override fun setOnOuterInterceptTouchEventListener(listener: (MotionEvent) -> Boolean) {
    this.interceptTouchEventListener = listener
  }

  override fun setOnOuterTouchEventListener(listener: (MotionEvent) -> Boolean) {
    this.touchEventListener = listener
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    attachedToWindow = true

    if (toolbar != null && !attachedToToolbar) {
      toolbar?.addCollapseCallback(this)
      attachedToToolbar = true
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    attachedToWindow = false

    if (attachedToToolbar) {
      toolbar?.removeCollapseCallback(this)
      attachedToToolbar = false
    }
  }

  override fun onCollapseTranslation(offset: Float) {
    lastCollapseTranslationOffset = offset

    if (isCollapseLocked) {
      return
    }

    val newAlpha = 1f - offset
    if (newAlpha == alpha) {
      return
    }

    if (animating) {
      return
    }

    this.alpha = newAlpha
  }

  override fun onCollapseAnimation(collapse: Boolean) {
    onCollapseAnimationInternal(collapse, true)
  }

  override fun setOnNavigationItemSelectedListener(listener: (Int) -> Boolean) {
    this.menuItemClickListener = listener
  }

  private fun onCollapseAnimationInternal(collapse: Boolean, isFromToolbarCallbacks: Boolean) {
    if (isFromToolbarCallbacks) {
      lastCollapseTranslationOffset = if (collapse) {
        1f
      } else {
        0f
      }
    }

    if (isTranslationLocked) {
      return
    }

    val newAlpha = if (collapse) {
      0f
    } else {
      1f
    }

    if (newAlpha == alpha) {
      return
    }

    animate().cancel()

    animate()
      .alpha(newAlpha)
      .setDuration(Toolbar.TOOLBAR_ANIMATION_DURATION_MS)
      .setInterpolator(Toolbar.TOOLBAR_ANIMATION_INTERPOLATOR)
      .setListener(object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator) {
          animating = false
        }

        override fun onAnimationCancel(animation: Animator) {
          animating = false
        }

        override fun onAnimationStart(animation: Animator) {
          animating = true
        }
      })
      .start()
  }

  private fun restoreHeightWithAnimation() {
    if (isTranslationLocked || isCollapseLocked) {
      return
    }

    val newAlpha = 1f - lastCollapseTranslationOffset
    if (newAlpha == alpha) {
      return
    }

    animate().cancel()

    animate()
      .alpha(newAlpha)
      .setDuration(Toolbar.TOOLBAR_ANIMATION_DURATION_MS)
      .setInterpolator(Toolbar.TOOLBAR_ANIMATION_INTERPOLATOR)
      .start()
  }

  private fun completelyDisableBottomNavigationView() {
    visibility = View.GONE
    isTranslationLocked = true
    isCollapseLocked = true
    this.setAlphaFast(0f)
  }

  companion object {

    fun isBottomNavViewEnabled(): Boolean {
      if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
        return false
      }

      return ChanSettings.isNavigationViewEnabled()
    }

    fun bottomNavViewButtons(): List<KurobaComposeIconPanel.MenuItem> {
      val bottomNavViewButtons = PersistableChanState.reorderableBottomNavViewButtons.get()

      return bottomNavViewButtons.bottomNavViewButtons().map { bottomNavViewButton ->
        return@map when (bottomNavViewButton) {
          BottomNavViewButton.Search -> {
            KurobaComposeIconPanel.MenuItem(
              id = R.id.action_search,
              iconId = R.drawable.ic_search_white_24dp
            )
          }
          BottomNavViewButton.Archive -> {
            KurobaComposeIconPanel.MenuItem(
              id = R.id.action_archive,
              iconId = R.drawable.ic_baseline_archive_24
            )
          }
          BottomNavViewButton.Bookmarks -> {
            KurobaComposeIconPanel.MenuItem(
              id = R.id.action_bookmarks,
              iconId = R.drawable.ic_bookmark_white_24dp
            )
          }
          BottomNavViewButton.Browse -> {
            KurobaComposeIconPanel.MenuItem(
              id = R.id.action_browse,
              iconId = R.drawable.ic_baseline_laptop
            )
          }
          BottomNavViewButton.Settings -> {
            KurobaComposeIconPanel.MenuItem(
              id = R.id.action_settings,
              iconId = R.drawable.ic_baseline_settings
            )
          }
        }
      }
    }

  }

}