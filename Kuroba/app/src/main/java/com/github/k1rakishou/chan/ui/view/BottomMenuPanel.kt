package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.dp
import javax.inject.Inject

class BottomMenuPanel @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attributeSet, defStyleAttr), WindowInsetsListener, ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val items = mutableListOf<BottomMenuPanelItem>()
  private var state = State.NotInitialized

  init {
    if (!isInEditMode) {
      Chan.inject(this)
    }

    orientation = HORIZONTAL
    gravity = Gravity.CENTER_HORIZONTAL
    visibility = View.INVISIBLE

    layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      AndroidUtils.getDimen(R.dimen.bottom_nav_view_height)
    )

    updateColors()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    themeEngine.addListener(this)

    ViewCompat.requestApplyInsets(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    if (state != State.Shown) {
      // Just dismiss all touch events if the user accidentally clicked this view when it's not
      // not supposed to be visible.
      return false
    }

    // Consume all clicks
    return true
  }

  override fun onInsetsChanged() {
    updatePaddings()
  }

  override fun onThemeChanged() {
    updateColors()
  }

  private fun updateColors() {
    setBackgroundColor(themeEngine.chanTheme.primaryColor)
  }

  fun onBack(): Boolean {
    if (state == State.NotInitialized || state == State.Hidden) {
      return false
    }

    hide()
    return true
  }

  fun show(items: List<BottomMenuPanelItem>) {
    require(state != State.NotInitialized) { "state is NotInitialized!" }

    if (state == State.Shown) {
      // TODO(KurobaEx): transition between two views if the state is shown and we are attempting to
      //  show new items
      return
    }

    this.items.clear()
    this.items.addAll(items)

    children.forEach { child -> removeView(child) }
    items.forEach { item ->
      val imageView = AppCompatImageView(context)

      imageView.setImageDrawable(ContextCompat.getDrawable(context, item.iconResId))
      imageView.layoutParams = LayoutParams(MENU_SIZE, MENU_SIZE)

      val frameLayout = FrameLayout(context)
      frameLayout.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        .apply { gravity = Gravity.CENTER_VERTICAL }

      frameLayout.setOnClickListener {
        postDelayed({
          hide()
          item.onClickListener.invoke(item.menuItemId)
        }, HIDE_DELAY)
      }

      AndroidUtils.setBoundlessRoundRippleBackground(frameLayout)
      frameLayout.updatePadding(left = MENU_ITEM_PADDING, right = MENU_ITEM_PADDING)

      frameLayout.addView(imageView)
      addView(frameLayout)
    }

    animate().cancel()
    animate()
      .translationY(0f)
      .setInterpolator(INTERPOLATOR)
      .setDuration(ANIMATION_DURATION)
      .withEndAction {
        state = State.Shown
      }
  }

  fun hide() {
    require(state != State.NotInitialized) { "state is NotInitialized!" }

    if (state == State.Hidden) {
      return
    }

    this.items.clear()

    animate().cancel()
    animate()
      .translationY(totalHeight().toFloat())
      .setInterpolator(INTERPOLATOR)
      .setDuration(ANIMATION_DURATION)
      .withEndAction {
        state = State.Hidden
      }
  }

  private fun updatePaddings() {
    layoutParams.height =
      AndroidUtils.getDimen(R.dimen.bottom_nav_view_height) + globalWindowInsetsManager.bottom()

    if (globalWindowInsetsManager.isKeyboardOpened) {
      updatePadding(bottom = 0)
    } else {
      updatePadding(bottom = globalWindowInsetsManager.bottom())
    }

    if (state == State.NotInitialized) {
      translationY = totalHeight().toFloat()

      visibility = View.VISIBLE
      state = State.Hidden
    }
  }

  fun totalHeight(): Int = layoutParams.height + paddingBottom

  enum class State {
    NotInitialized,
    Hidden,
    Shown
  }

  enum class MenuItemId {
    Delete
  }

  class BottomMenuPanelItem(
    val menuItemId: MenuItemId,
    @DrawableRes val iconResId: Int,
    val onClickListener: (MenuItemId) -> Unit
  )

  companion object {
    private const val ANIMATION_DURATION = 250L
    private const val HIDE_DELAY = 100L

    private val MENU_ITEM_PADDING = dp(24f)
    private val MENU_SIZE = dp(32f)
    private val INTERPOLATOR = FastOutSlowInInterpolator()
  }
}