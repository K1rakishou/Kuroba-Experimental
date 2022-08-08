package com.github.k1rakishou.chan.ui.view.bottom_menu_panel

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class BottomMenuPanel @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr), WindowInsetsListener, ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val items = mutableListOf<BottomMenuPanelItem>()
  private var state = State.NotInitialized

  private var onBottomStateChangedFunc: ((State) -> Unit)? = null

  val isBottomPanelShown: Boolean
    get() = state == State.Shown

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }

    visibility = View.INVISIBLE

    layoutParams = FrameLayout.LayoutParams(
      LayoutParams.MATCH_PARENT,
      getDimen(R.dimen.navigation_view_size)
    )

    updateColors()
  }

  fun onBottomPanelStateChanged(func: (State) -> Unit) {
    onBottomStateChangedFunc = func
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
    children.forEach { child -> child.setBackgroundColor(themeEngine.chanTheme.primaryColor) }
  }

  fun onBack(): Boolean {
    Logger.d(TAG, "onBack(${items.size})")

    if (state == State.NotInitialized || state == State.Hidden) {
      return false
    }

    hide()
    return true
  }

  fun show(items: List<BottomMenuPanelItem>) {
    Logger.d(TAG, "show(${items.size}), prevState=${state}")

    if (state == State.Shown) {
      if (!itemsDiffer(this.items, items)) {
        return
      }

      transitionBetweenViews(items)
      return
    }

    this.items.clear()
    this.items.addAll(items)
    removeAllViews()

    val containerLinearLayout = LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_HORIZONTAL
    }

    containerLinearLayout.layoutParams = LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )

    items.forEach { item ->
      val linearLayout = createMenuItemView(item)
      containerLinearLayout.addView(linearLayout)
    }

    addView(containerLinearLayout)

    animate().cancel()
    animate()
      .translationY(0f)
      .alpha(1f)
      .setInterpolator(INTERPOLATOR)
      .setDuration(ANIMATION_DURATION)
      .withStartAction { alpha = 0f }
      .withEndAction {
        state = State.Shown
        onBottomStateChangedFunc?.invoke(state)
        Logger.d(TAG, "show(${items.size}), state=${state}")
      }
      .start()
  }

  fun hide() {
    Logger.d(TAG, "hide(${items.size}), prevState=${state}")

    if (state == State.Hidden || state == State.NotInitialized) {
      return
    }

    this.items.clear()

    animate().cancel()
    animate()
      .translationY(totalHeight().toFloat())
      .alpha(0f)
      .setInterpolator(INTERPOLATOR)
      .setDuration(ANIMATION_DURATION)
      .withStartAction { alpha = 1f }
      .withEndAction {
        state = State.Hidden
        onBottomStateChangedFunc?.invoke(state)
        Logger.d(TAG, "hide(${items.size}), state=${state}")
      }
      .start()
  }

  private fun transitionBetweenViews(items: List<BottomMenuPanelItem>) {
    Logger.d(TAG, "hide(${items.size}), state=${state}, childCount=${childCount}")

    if (childCount != 1) {
      return
    }

    val prevLinearLayout = getChildAt(0)
    require(prevLinearLayout is LinearLayout) {
      "prevLinearLayout is not LinearLayout! prevLinearLayout is ${prevLinearLayout.javaClass.simpleName}"
    }

    prevLinearLayout.animate().cancel()

    this.items.clear()
    this.items.addAll(items)

    val newLinearLayout = LinearLayout(context)
    newLinearLayout.layoutParams = LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    newLinearLayout.gravity = Gravity.CENTER_HORIZONTAL

    items.forEach { item ->
      val linearLayout = createMenuItemView(item)
      newLinearLayout.addView(linearLayout)
    }

    newLinearLayout.visibility = View.VISIBLE
    newLinearLayout.alpha = 0f

    addView(newLinearLayout)

    prevLinearLayout
      .animate()
      .alpha(0f)
      .setInterpolator(INTERPOLATOR)
      .setDuration(ANIMATION_DURATION)
      .start()

    newLinearLayout
      .animate()
      .alpha(1f)
      .setInterpolator(INTERPOLATOR)
      .setDuration(ANIMATION_DURATION)
      .withEndAction {
        removeView(prevLinearLayout)
        require(childCount == 1) { "Bad childCount: ${childCount}" }
      }
      .start()
  }

  private fun createMenuItemView(item: BottomMenuPanelItem): LinearLayout {
    val imageView = AppCompatImageView(context)

    val drawable = ContextCompat.getDrawable(context, item.iconResId)!!
    DrawableCompat.setTint(drawable, Color.WHITE)

    imageView.setImageDrawable(drawable)
    imageView.layoutParams = LinearLayout.LayoutParams(MENU_SIZE, MENU_SIZE).apply {
      gravity = Gravity.CENTER_HORIZONTAL
    }

    val linearLayout = LinearLayout(context)
    linearLayout.orientation = LinearLayout.VERTICAL
    linearLayout.layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      .apply { gravity = Gravity.CENTER_VERTICAL }

    linearLayout.setOnClickListener {
      postDelayed({
        item.onClickListener.invoke(item.menuItemId)
      }, HIDE_DELAY)
    }

    AndroidUtils.setBoundlessRoundRippleBackground(linearLayout)

    val padding = if (isTablet()) {
      MENU_ITEM_PADDING * 2
    } else {
      MENU_ITEM_PADDING
    }

    linearLayout.updatePadding(left = padding, right = padding)

    val textView = AppCompatTextView(context)
    textView.textSize = 11f
    textView.text = context.getString(item.textResId)
    textView.setTextColor(Color.WHITE)
    textView.layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    linearLayout.addView(imageView)
    linearLayout.addView(textView)
    return linearLayout
  }

  private fun itemsDiffer(
    oldItems: List<BottomMenuPanelItem>,
    newItems: List<BottomMenuPanelItem>
  ): Boolean {
    val oldItemIdSet = oldItems.map { item -> item.menuItemId.id() }.toSet()
    val newItemIdSet = newItems.map { item -> item.menuItemId.id() }.toSet()

    if (oldItemIdSet.size != newItemIdSet.size) {
      return true
    }

    return oldItemIdSet != newItemIdSet
  }

  private fun updatePaddings() {
    layoutParams.height = getDimen(R.dimen.navigation_view_size) + globalWindowInsetsManager.bottom()

    if (globalWindowInsetsManager.isKeyboardOpened) {
      updatePadding(bottom = 0)
    } else {
      updatePadding(bottom = globalWindowInsetsManager.bottom())
    }

    Logger.d(TAG, "updatePaddings() state=${state}")

    if (state == State.NotInitialized) {
      translationY = totalHeight().toFloat()

      visibility = View.VISIBLE
      state = State.Hidden

      Logger.d(TAG, "updatePaddings() state=${state}, visibility: ${visibility}, translationY=${translationY}")
    }
  }

  fun totalHeight(): Int = layoutParams.height

  enum class State {
    NotInitialized,
    Hidden,
    Shown
  }

  companion object {
    private const val TAG = "BottomMenuPanel"

    private const val ANIMATION_DURATION = 250L
    private const val HIDE_DELAY = 100L

    private val MENU_ITEM_PADDING = dp(12f)
    private val MENU_SIZE = dp(24f)
    private val INTERPOLATOR = FastOutSlowInInterpolator()
  }
}