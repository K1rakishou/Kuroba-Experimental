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
package com.github.k1rakishou.chan.controller.transition

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.doOnPreDraw

class PushControllerTransition : ControllerTransition() {

  override fun perform() {
    to!!.view.doOnPreDraw {
      animatorSet.end()

      val toAlpha: Animator = ObjectAnimator.ofFloat(to!!.view, View.ALPHA, 0f, 1f)
      toAlpha.duration = 200
      toAlpha.interpolator = DecelerateInterpolator(2f)

      val toY: Animator = ObjectAnimator.ofFloat(to!!.view, View.TRANSLATION_Y, to!!.view.height * 0.08f, 0f)
      toY.duration = 350
      toY.interpolator = DecelerateInterpolator(2.5f)

      toY.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationCancel(animation: Animator) {
          onCompleted()
        }

        override fun onAnimationEnd(animation: Animator) {
          onCompleted()
        }
      })

      animatorSet.playTogether(toAlpha, toY)
      animatorSet.start()
    }
  }

}