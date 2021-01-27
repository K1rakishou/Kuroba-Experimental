package com.github.k1rakishou.chan.ui.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeAnimator
import android.animation.ValueAnimator

abstract class BaseAnimation {
  protected var animatorSet: AnimatorSet? = null

  fun end() {
    animatorSet?.apply {
      endAnimations()
    }

    animatorSet = null
  }

  fun isRunning(): Boolean {
    return animatorSet?.isRunning ?: false
  }

}

fun AnimatorSet.cancelAnimations() {
  cancel()

  recursivelyClearCallbacks(childAnimations)
  removeAllListeners()
}

fun AnimatorSet.endAnimations() {
  end()

  recursivelyClearCallbacks(childAnimations)
  removeAllListeners()
}

// Yes we need to remove all of those listeners. Otherwise stuff will leak.
private fun recursivelyClearCallbacks(childAnimations: ArrayList<Animator>) {
  childAnimations.forEach { childAnimation ->
    when (childAnimation) {
      is ValueAnimator -> {
        childAnimation.removeAllUpdateListeners()
        childAnimation.removeAllListeners()
      }
      is ObjectAnimator -> {
        childAnimation.removeAllUpdateListeners()
        childAnimation.removeAllListeners()
      }
      is TimeAnimator -> {
        childAnimation.removeAllUpdateListeners()
        childAnimation.removeAllListeners()
      }
      is AnimatorSet -> {
        recursivelyClearCallbacks(childAnimation.childAnimations)
        childAnimation.removeAllListeners()
      }
    }
  }
}