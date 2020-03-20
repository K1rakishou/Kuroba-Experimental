package com.github.adamantcheese.chan.ui.animation

import android.animation.*

abstract class BaseAnimation {
    protected var animatorSet: AnimatorSet? = null

    fun end() {
        animatorSet?.apply {
            end()

            recursivelyClearCallbacks(childAnimations)
            removeAllListeners()
        }

        animatorSet = null
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
}