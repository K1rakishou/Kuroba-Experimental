package com.github.adamantcheese.chan.ui.animation

import android.animation.AnimatorSet

abstract class BaseAnimation {
    protected var animatorSet: AnimatorSet? = null

    fun end() {
        animatorSet?.apply {
            end()
            removeAllListeners()
        }

        animatorSet = null
    }
}