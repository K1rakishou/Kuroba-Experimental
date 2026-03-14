package com.github.k1rakishou.chan.ui.layout

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.ViewGroup
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.view.widget.SlidingPaneLayoutEx
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.v2.KurobaSettings
import javax.inject.Inject

class ThreadSlidingPaneLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : SlidingPaneLayoutEx(
  context, attrs, defStyle
) {
  @Inject
  lateinit var kurobaSettings: KurobaSettings
  @Inject
  lateinit var themeEngine: ThemeEngine

  @JvmField
  var leftPane: ViewGroup? = null
  @JvmField
  var rightPane: ViewGroup? = null

  private var threadSlideController: ThreadSlideController? = null

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    leftPane = findViewById<ViewGroup>(R.id.left_pane)
    rightPane = findViewById<ViewGroup>(R.id.right_pane)
    setOverhangSize(currentOverhangSize())
  }

  private fun currentOverhangSize(): Int {
    if (kurobaSettings.application.isSlideLayoutModeBlocking()) {
      return SLIDE_PANE_OVERHANG_SIZE
    }

    return 0
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    // Forces a relayout after it has already been layed out, because SlidingPaneLayout sucks and otherwise
    // gives the children too much room until they request a relayout.
    AppModuleAndroidUtils.waitForLayout(this) {
      requestLayout()
      false
    }
  }

  fun setThreadSlideController(slideController: ThreadSlideController) {
    threadSlideController = slideController
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    super.onRestoreInstanceState(state)

    threadSlideController?.onSlidingPaneLayoutStateRestored()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)

    val leftParams = leftPane?.layoutParams
      ?: return
    val rightParams = rightPane?.layoutParams
      ?: return

    leftParams.width = width - currentOverhangSize()
    rightParams.width = width
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
  }

  companion object {
    private val SLIDE_PANE_OVERHANG_SIZE = AppModuleAndroidUtils.dp(20f)
  }

}
