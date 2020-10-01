package com.github.k1rakishou.chan.ui.widget

import android.view.View

class SlidingPaneLayoutSlideHandler(
    private val initiallyOpen: Boolean
) : SlidingPaneLayoutEx.PanelSlideListener {
    private val listeners = mutableSetOf<SlidingPaneLayoutSlideListener>()
    private var sliding = false

    fun addListener(listener: SlidingPaneLayoutSlideListener) {
        listeners += listener
    }

    fun removeListener(listener: SlidingPaneLayoutSlideListener) {
        listeners -= listener
    }

    fun clearListeners() {
        listeners.clear()
    }

    override fun onPanelSlide(panel: View, slideOffset: Float) {
        if (!sliding) {
            listeners.forEach { it.onSlidingStarted(initiallyOpen) }
            sliding = true
        }

        listeners.forEach { it.onSliding(slideOffset) }
    }

    override fun onPanelOpened(panel: View) {
        sliding = false

        listeners.forEach { it.onSlidingEnded(true) }
    }

    override fun onPanelClosed(panel: View) {
        sliding = false

        listeners.forEach { it.onSlidingEnded(false) }
    }

    interface SlidingPaneLayoutSlideListener {
        fun onSlidingStarted(wasOpen: Boolean)
        fun onSliding(offset: Float)
        fun onSlidingEnded(becameOpen: Boolean)
    }

}