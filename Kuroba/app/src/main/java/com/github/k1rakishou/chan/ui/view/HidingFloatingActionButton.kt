/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.view

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.Toolbar.ToolbarCollapseCallback
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.common.updateMargins
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import javax.inject.Inject

class HidingFloatingActionButton : ColorizableFloatingActionButton, ToolbarCollapseCallback, WindowInsetsListener {
  private var attachedToWindow = false
  private var toolbar: Toolbar? = null
  private var attachedToToolbar = false
  private var coordinatorLayout: CoordinatorLayout? = null
  private var currentCollapseScale = 0f
  private var bottomNavViewHeight = 0
  private var listeningForInsetsChanges = false
  private var animating = false
  private var isCatalogFloatingActionButton: Boolean? = null

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val isSnackbarShowing: Boolean
    get() {
      for (i in 0 until coordinatorLayout!!.childCount) {
        if (coordinatorLayout!!.getChildAt(i) is SnackbarLayout) {
          currentCollapseScale = -1f
          return true
        }
      }

      return false
    }

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    init()
  }

  private fun init() {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractStartActivityComponent(context)
        .inject(this)

      // We apply the bottom paddings directly in SplitNavigationController when we are in SPLIT
      // mode, so we don't need to do that twice and that's why we set bottomNavViewHeight to 0
      // when in SPLIT mode.
      bottomNavViewHeight = if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
        getDimen(R.dimen.bottom_nav_view_height)
      } else {
        0
      }

      startListeningForInsetsChangesIfNeeded()
    }
  }

  fun setToolbar(toolbar: Toolbar) {
    this.toolbar = toolbar
    updatePaddings()

    if (attachedToWindow && !attachedToToolbar) {
      toolbar.addCollapseCallback(this)
      attachedToToolbar = true
    }
  }

  fun setIsCatalogFloatingActionButton(isCatalog: Boolean) {
    isCatalogFloatingActionButton = isCatalog
  }

  override fun show() {
    if (isCurrentReplyLayoutOpened()) {
      return
    }

    super.show()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    attachedToWindow = true

    require(parent is CoordinatorLayout) { "HidingFloatingActionButton must be a parent of CoordinatorLayout" }
    coordinatorLayout = parent as CoordinatorLayout

    if (toolbar != null && !attachedToToolbar) {
      toolbar!!.addCollapseCallback(this)
      attachedToToolbar = true
    }

    startListeningForInsetsChangesIfNeeded()
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    if (this.visibility != View.VISIBLE || animating) {
      return false
    }

    return super.onTouchEvent(ev)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    attachedToWindow = false
    if (attachedToToolbar) {
      toolbar!!.removeCollapseCallback(this)
      attachedToToolbar = false
    }

    stopListeningForInsetsChanges()
    coordinatorLayout = null
  }

  override fun onInsetsChanged() {
    updatePaddings()
  }

  override fun onCollapseTranslation(offset: Float) {
    if (isSnackbarShowing) {
      return
    }

    if (offset >= 1f && isCurrentReplyLayoutOpened()) {
      return
    }

    if (offset != currentCollapseScale) {
      currentCollapseScale = 1f - offset

      if (offset < 1f) {
        if (visibility != VISIBLE) {
          visibility = VISIBLE
        }
      } else {
        if (visibility != GONE) {
          visibility = GONE
        }
      }

      scaleX = currentCollapseScale
      scaleY = currentCollapseScale
    }
  }

  override fun onCollapseAnimation(collapse: Boolean) {
    if (isSnackbarShowing) {
      return
    }

    if (!collapse && isCurrentReplyLayoutOpened()) {
      // Prevent showing FAB when the reply layout is opened
      return
    }

    val scale = if (collapse) {
      0f
    } else {
      1f
    }

    if (scale != currentCollapseScale) {
      currentCollapseScale = scale

      animate()
        .scaleX(currentCollapseScale)
        .scaleY(currentCollapseScale)
        .setDuration(300)
        .setStartDelay(0)
        .setListener(object : SimpleAnimatorListener() {
          override fun onAnimationEnd(animation: Animator?) {
            animating = false
          }

          override fun onAnimationCancel(animation: Animator?) {
            animating = false
          }

          override fun onAnimationStart(animation: Animator?) {
            animating = true
          }
        })
        .setInterpolator(SLOWDOWN)
        .start()
    }
  }

  private fun stopListeningForInsetsChanges() {
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    listeningForInsetsChanges = false
  }

  private fun startListeningForInsetsChangesIfNeeded() {
    if (listeningForInsetsChanges) {
      return
    }

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    listeningForInsetsChanges = true
  }

  private fun updatePaddings() {
    val fabBottomMargin = getDimen(R.dimen.hiding_fab_margin)
    updateMargins(bottom = fabBottomMargin + bottomNavViewHeight + globalWindowInsetsManager.bottom())
  }

  private fun isCurrentReplyLayoutOpened(): Boolean {
    val threadLayout = findThreadLayout()
      ?: return true
    val isCatalogButton = isCatalogFloatingActionButton
      ?: return true
    val isCatalogReplyLayout = threadLayout.isCatalogReplyLayout()
      ?: return true

    if (isCatalogButton == isCatalogReplyLayout && threadLayout.isReplyLayoutOpen()) {
      return true
    }

    return false
  }

  private fun findThreadLayout(): ThreadLayout? {
    var parent = this.parent

    while (parent != null && parent !is ThreadLayout) {
      parent = parent.parent
    }

    return parent as? ThreadLayout
  }

  companion object {
    private val SLOWDOWN = DecelerateInterpolator(2f)
  }
}