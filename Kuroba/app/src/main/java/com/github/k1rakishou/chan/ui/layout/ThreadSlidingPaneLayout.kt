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
package com.github.k1rakishou.chan.ui.layout

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.ViewGroup
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.widget.SlidingPaneLayoutEx
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class ThreadSlidingPaneLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : SlidingPaneLayoutEx(
  context, attrs, defStyle
) {
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
    if (ChanSettings.isSlideLayoutMode()) {
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
