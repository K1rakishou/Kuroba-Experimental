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

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.view.updatePadding
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.animation.cancelAnimations
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.Toolbar.ToolbarCollapseCallback
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import javax.inject.Inject

class HidingFloatingActionButton
  : AppCompatImageView,
  ToolbarCollapseCallback,
  WindowInsetsListener,
  ThemeEngine.ThemeChangesListener {

  private var attachedToWindow = false
  private var toolbar: Toolbar? = null
  private var attachedToToolbar = false
  private var coordinatorLayout: CoordinatorLayout? = null
  private var bottomNavViewHeight = 0

  private var listeningForInsetsChanges = false
  private var focused = false
  private var isThreadMode = false
  private var currentFabAnimation = CurrentFabAnimation.None
  private var threadControllerType: ThreadSlideController.ThreadControllerType? = null
  private var animatorSet: AnimatorSet? = null

  private val padding = dp(12f)
  private val additionalPadding = dp(17f)
  private val hatOffsetX = dp(7f).toFloat()
  private val isChristmasToday = TimeUtils.isChristmasToday()

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val hat by lazy { BitmapFactory.decodeResource(resources, R.drawable.christmashat)!! }
  private val paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
  private val outlinePath = Path()
  private val fabOutlineProvider = FabOutlineProvider(outlinePath)

  private val paddingL = if (isChristmasToday) {
    additionalPadding + padding
  } else {
    padding
  }
  private val paddingT = if (isChristmasToday) {
    additionalPadding + padding
  } else {
    padding
  }

  private val isSnackbarShowing: Boolean
    get() {
      val layout = coordinatorLayout
        ?: return false

      for (i in 0 until layout.childCount) {
        if (layout.getChildAt(i) is SnackbarLayout) {
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

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    init()
  }

  private fun init() {
    setWillNotDraw(false)

    updatePadding(left = paddingL, top = paddingT, right = padding, bottom = padding)

    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)

      // We apply the bottom paddings directly in SplitNavigationController when we are in SPLIT
      // mode, so we don't need to do that twice and that's why we set bottomNavViewHeight to 0
      // when in SPLIT mode.
      bottomNavViewHeight = if (KurobaBottomNavigationView.isBottomNavViewEnabled()) {
        getDimen(R.dimen.navigation_view_size)
      } else {
        0
      }

      startListeningForInsetsChangesIfNeeded()
      onThemeChanged()
    }

    outlineProvider = fabOutlineProvider

    setVisibilityFast(GONE)
    setAlphaFast(0f)
  }

  fun setToolbar(toolbar: Toolbar) {
    this.toolbar = toolbar
    updatePaddings()

    if (attachedToWindow && !attachedToToolbar) {
      toolbar.addCollapseCallback(this)
      attachedToToolbar = true
    }
  }

  fun setThreadControllerType(threadControllerType: ThreadSlideController.ThreadControllerType) {
    this.threadControllerType = threadControllerType
  }

  fun setThreadVisibilityState(isThreadMode: Boolean) {
    this.isThreadMode = isThreadMode
  }

  fun gainedFocus(threadControllerType: ThreadSlideController.ThreadControllerType) {
    focused = threadControllerType == this.threadControllerType
  }

  fun lostFocus(threadControllerType: ThreadSlideController.ThreadControllerType) {
    if (ChanSettings.neverHideToolbar.get()) {
      return
    }

    val prevFocused = focused
    focused = (threadControllerType == this.threadControllerType).not()

    if (!focused && focused != prevFocused) {
      onCollapseAnimationInternal(collapse = true, forced = true)
    }
  }

  fun isFabVisible(): Boolean {
    return visibility == View.VISIBLE && currentFabAnimation != CurrentFabAnimation.Hiding
  }

  fun hide() {
    onCollapseAnimation(collapse = true)
  }

  fun show() {
    onCollapseAnimation(collapse = false)
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    if (this.visibility != View.VISIBLE || currentFabAnimation != CurrentFabAnimation.None) {
      return false
    }

    return super.onTouchEvent(ev)
  }

  override fun onThemeChanged() {
    setBackgroundColor(themeEngine.chanTheme.accentColor)
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

    if (!isInEditMode) {
      startListeningForInsetsChangesIfNeeded()
      themeEngine.addListener(this)
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    cancelPrevAnimation()
    attachedToWindow = false

    if (attachedToToolbar) {
      toolbar!!.removeCollapseCallback(this)
      attachedToToolbar = false
    }

    stopListeningForInsetsChanges()
    themeEngine.removeListener(this)

    coordinatorLayout = null
  }

  override fun onInsetsChanged() {
    updatePaddings()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    updatePath(measuredWidth, measuredHeight)
  }

  override fun draw(canvas: Canvas) {
    canvas.withSave {
      canvas.clipPath(outlinePath)
      super.draw(canvas)
    }

    if (isChristmasToday) {
      canvas.withScale(x = 0.5f, y = 0.5f, pivotX = 0.5f, pivotY = 0.5f) {
        canvas.withTranslation(x = hatOffsetX) {
          canvas.drawBitmap(hat, 0f, 0f, paint)
        }
      }
    }
  }

  private fun updatePath(inputWidthPx: Int, inputHeightPx: Int) {
    val widthPx = if (isChristmasToday) {
      inputWidthPx - additionalPadding
    } else {
      inputWidthPx
    }

    val heightPx = if (isChristmasToday) {
      inputHeightPx - additionalPadding
    } else {
      inputHeightPx
    }

    val offsetX = if (isChristmasToday) additionalPadding else 0
    val offsetY = if (isChristmasToday) additionalPadding else 0

    val centerX = offsetX + (widthPx / 2f)
    val centerY = offsetY + (heightPx / 2f)

    outlinePath.reset()
    outlinePath.addCircle(centerX, centerY, widthPx / 2f, Path.Direction.CW)
    outlinePath.close()
  }

  override fun onCollapseTranslation(offset: Float) {
    if (!isThreadMode) {
      // We are either showing an error or an empty message or loading view, so we can't update the
      // FAB state.
      return
    }

    if (isSnackbarShowing || !focused) {
      return
    }

    val newFabAlpha = 1f - offset

    if (newFabAlpha >= 1f && isCurrentReplyLayoutOpened()) {
      // If trying to show and currently reply layout is opened - do not show.
      return
    }

    if (newFabAlpha != this.alpha) {
      cancelPrevAnimation()

      if (newFabAlpha < 0.5f) {
        setVisibilityFast(GONE)
      } else if (newFabAlpha > 0.5f) {
        setVisibilityFast(VISIBLE)
      }

      setAlphaFast(newFabAlpha)
    }
  }

  override fun onCollapseAnimation(collapse: Boolean) {
    onCollapseAnimationInternal(collapse)
  }

  private fun onCollapseAnimationInternal(collapse: Boolean, forced: Boolean = false) {
    if (collapse && currentFabAnimation == CurrentFabAnimation.Hiding
      || !collapse && currentFabAnimation == CurrentFabAnimation.Showing
    ) {
      // Exactly this animation is already running
      return
    }

    if (!collapse && !isThreadMode) {
      // We can only hide the FAB when we are not showing the posts
      return
    }

    if (isSnackbarShowing && !collapse) {
      // Can't show FAB when snackbar is visible
      return
    }

    if (!focused && !forced) {
      // If not focused ignore everything (except for cases when it is a forced update).
      return
    }

    if (!collapse && isCurrentReplyLayoutOpened()) {
      // Prevent showing FAB when the reply layout is opened
      return
    }

    cancelPrevAnimation()

    val newFabAlpha = if (collapse) {
      0f
    } else {
      1f
    }

    currentFabAnimation = if (collapse) {
      CurrentFabAnimation.Hiding
    } else {
      CurrentFabAnimation.Showing
    }

    val alphaAnimation = ValueAnimator.ofFloat(this.alpha, newFabAlpha).apply {
      duration = Toolbar.TOOLBAR_ANIMATION_DURATION_MS
      interpolator = Toolbar.TOOLBAR_ANIMATION_INTERPOLATOR

      doOnStart {
        if (!collapse) {
          this@HidingFloatingActionButton.setVisibilityFast(VISIBLE)
        }
      }

      fun onCancelOrEnd() {
        currentFabAnimation = CurrentFabAnimation.None

        if (collapse) {
          this@HidingFloatingActionButton.setAlphaFast(0f)
          this@HidingFloatingActionButton.setVisibilityFast(GONE)
        } else {
          this@HidingFloatingActionButton.setAlphaFast(1f)
          this@HidingFloatingActionButton.setVisibilityFast(VISIBLE)
        }
      }

      doOnCancel { onCancelOrEnd() }
      doOnEnd { onCancelOrEnd() }

      addUpdateListener { animation ->
        val newAlpha = animation.animatedValue as Float

        this@HidingFloatingActionButton.setAlphaFast(newAlpha)
      }
    }

    animatorSet = AnimatorSet().apply {
      play(alphaAnimation)
      start()
    }
  }

  private fun cancelPrevAnimation() {
    if (animatorSet != null) {
      animatorSet?.cancelAnimations()
      animatorSet = null
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
    val controllerType = threadControllerType
      ?: return true
    val threadLayout = findThreadLayout(controllerType)
      ?: return true
    val threadLayoutThreadControllerType = threadLayout.threadControllerType
      ?: return true

    if (controllerType == threadLayoutThreadControllerType && threadLayout.isReplyLayoutOpen()) {
      return true
    }

    return false
  }

  private fun findThreadLayout(controllerType: ThreadSlideController.ThreadControllerType): ThreadLayout? {
    var parent = this.parent

    while (parent != null && parent !is ThreadLayout) {
      if (parent is ThreadLayout && parent.threadControllerType == controllerType) {
        break
      }

      parent = parent.parent
    }

    return parent as? ThreadLayout
  }

  private class FabOutlineProvider(
    private val path: Path
  ) : ViewOutlineProvider() {

    override fun getOutline(view: View, outline: Outline) {
      outline.setConvexPath(path)
    }

  }

  enum class CurrentFabAnimation {
    None,
    Hiding,
    Showing
  }

}