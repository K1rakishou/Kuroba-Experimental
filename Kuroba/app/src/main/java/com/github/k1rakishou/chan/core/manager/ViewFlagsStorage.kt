package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.ui.view.FastScroller

class ViewFlagsStorage {
  private val fastScrollerDragStateMap = mutableMapOf<FastScroller.FastScrollerType, Boolean>()

  fun updateIsDraggingFastScroller(fastScrollerType: FastScroller.FastScrollerType, dragging: Boolean) {
    fastScrollerDragStateMap[fastScrollerType] = dragging
  }

  fun isDraggingFastScroller(fastScrollerType: FastScroller.FastScrollerType): Boolean {
    return fastScrollerDragStateMap[fastScrollerType] ?: false
  }

}