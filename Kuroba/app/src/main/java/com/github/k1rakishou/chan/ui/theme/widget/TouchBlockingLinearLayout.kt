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
package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import javax.inject.Inject

class TouchBlockingLinearLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0
) : LinearLayout(context, attributeSet, defStyle), IColorizableWidget {

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      Chan.inject(this)
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    return true
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    applyColors()
  }

  override fun applyColors() {
    setBackgroundColor(themeEngine.chanTheme.primaryColor)
  }

}