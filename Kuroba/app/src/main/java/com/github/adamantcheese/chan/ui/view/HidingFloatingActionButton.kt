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
package com.github.adamantcheese.chan.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.layout.ThreadLayout
import com.github.adamantcheese.chan.ui.toolbar.Toolbar
import com.github.adamantcheese.chan.ui.toolbar.Toolbar.ToolbarCollapseCallback
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.common.updateMargins
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class HidingFloatingActionButton : FloatingActionButton, ToolbarCollapseCallback {
  private var attachedToWindow = false
  private var toolbar: Toolbar? = null
  private var attachedToToolbar = false
  private var coordinatorLayout: CoordinatorLayout? = null
  private var currentCollapseScale = 0f
  private var bottomNavViewHeight = 0
  private var listeningForInsetsChanges = false
  private val compositeDisposable = CompositeDisposable()

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
    Chan.inject(this)

    // We apply the bottom paddings directly in SplitNavigationController when we are in SPLIT
    // mode, so we don't need to do that twice and that's why we set bottomNavViewHeight to 0
    // when in SPLIT mode.
    bottomNavViewHeight = if (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT) {
      AndroidUtils.getDimen(R.dimen.bottom_nav_view_height)
    } else {
      0
    }

    startListeningForInsetsChangesIfNeeded()
  }

  fun setToolbar(toolbar: Toolbar) {
    this.toolbar = toolbar
    updatePaddings()

    if (attachedToWindow && !attachedToToolbar) {
      toolbar.addCollapseCallback(this)
      attachedToToolbar = true
    }
  }

  override fun show() {
    val isReplyLayoutOpen = findThreadLayout()?.isReplyLayoutOpen() ?: false
    if (isReplyLayoutOpen) {
      return
    }

    super.show()
  }

  private fun findThreadLayout(): ThreadLayout? {
    var parent = this.parent

    while (parent != null && parent !is ThreadLayout) {
      parent = parent.parent
    }

    return parent as? ThreadLayout
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

  private fun stopListeningForInsetsChanges() {
    compositeDisposable.clear()
    listeningForInsetsChanges = false
  }

  private fun startListeningForInsetsChangesIfNeeded() {
    if (listeningForInsetsChanges) {
      return
    }

    val disposable = globalWindowInsetsManager.listenForInsetsChanges()
      .subscribe { updatePaddings() }

    compositeDisposable.add(disposable)
    listeningForInsetsChanges = true
  }

  private fun updatePaddings() {
    val fabBottomMargin = AndroidUtils.getDimen(R.dimen.hiding_fab_margin)

    updateMargins(bottom = fabBottomMargin + bottomNavViewHeight + globalWindowInsetsManager.bottom())
  }

  override fun onCollapseTranslation(offset: Float) {
    if (isSnackbarShowing) {
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

    isClickable = !collapse
    isFocusable = !collapse

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
        .setInterpolator(SLOWDOWN)
        .start()
    }
  }

  companion object {
    private val SLOWDOWN = DecelerateInterpolator(2f)
  }
}