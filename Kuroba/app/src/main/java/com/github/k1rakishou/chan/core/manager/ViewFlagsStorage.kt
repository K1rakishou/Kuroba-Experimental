package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.view.FastScroller

class ViewFlagsStorage {
  private val fastScrollerDragStateMap = mutableMapOf<FastScroller.FastScrollerType, Boolean>()
  private val replyLayoutOpenStateMap = mutableMapOf<ThreadSlideController.ThreadControllerType, Boolean>()

  fun updateIsDraggingFastScroller(fastScrollerType: FastScroller.FastScrollerType, dragging: Boolean) {
    fastScrollerDragStateMap[fastScrollerType] = dragging
  }

  fun isDraggingFastScroller(fastScrollerType: FastScroller.FastScrollerType): Boolean {
    return fastScrollerDragStateMap[fastScrollerType] ?: false
  }

  fun updateIsReplyLayoutOpened(threadControllerType: ThreadSlideController.ThreadControllerType, isOpened: Boolean) {
    replyLayoutOpenStateMap[threadControllerType] = isOpened
  }

  fun isReplyLayoutOpened(threadControllerType: ThreadSlideController.ThreadControllerType): Boolean {
    return replyLayoutOpenStateMap[threadControllerType] ?: false
  }

}