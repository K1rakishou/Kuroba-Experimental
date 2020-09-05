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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator

class PopControllerTransition(animatorSet: AnimatorSet) : ControllerTransition(animatorSet) {

  override fun perform() {
    animatorSet.end()

    val fromY: Animator = ObjectAnimator.ofFloat(from!!.view, View.TRANSLATION_Y, 0f, from!!.view.height * 0.05f)
    fromY.interpolator = AccelerateInterpolator(2.5f)
    fromY.duration = 250

    fromY.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationCancel(animation: Animator) {
        onCompleted()
      }

      override fun onAnimationEnd(animation: Animator) {
        onCompleted()
      }
    })

    val fromAlpha: Animator = ObjectAnimator.ofFloat(from!!.view, View.ALPHA, from!!.view.alpha, 0f)
    fromAlpha.interpolator = AccelerateInterpolator(2f)
    fromAlpha.startDelay = 100
    fromAlpha.duration = 150

    animatorSet.playTogether(fromY, fromAlpha)
    animatorSet.start()
  }

}