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
package com.github.adamantcheese.chan.controller.transition

import android.animation.AnimatorSet
import com.github.adamantcheese.chan.controller.Controller
import java.util.*

abstract class ControllerTransition(
  protected val animatorSet: AnimatorSet
) {
  private var callback: Callback? = null
  @JvmField
  var from: Controller? = null
  @JvmField
  var to: Controller? = null

  fun onCompleted() {
    if (callback == null) {
      throw NullPointerException("Callback cannot be null here!")
    }

    callback!!.onControllerTransitionCompleted(this)
  }

  fun setCallback(callback: Callback?) {
    this.callback = callback
  }

  fun debugInfo(): String {
    return String.format(
      Locale.ENGLISH,
      "callback=" + callback!!.javaClass.simpleName + ", " +
        "from=" + from!!.javaClass.simpleName + ", " +
        "to=" + to!!.javaClass.simpleName
    )
  }

  fun interface Callback {
    fun onControllerTransitionCompleted(transition: ControllerTransition?)
  }

  abstract fun perform()
}