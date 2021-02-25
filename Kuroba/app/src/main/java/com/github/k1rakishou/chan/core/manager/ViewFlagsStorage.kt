package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.view.FastScroller

class ViewFlagsStorage {
  private val fastScrollerDragStateMap = mutableMapOf<FastScroller.FastScrollerControllerType, Boolean>()
  private val replyLayoutOpenStateMap = mutableMapOf<ThreadSlideController.ThreadControllerType, Boolean>()

  fun updateIsDraggingFastScroller(fastScrollerControllerType: FastScroller.FastScrollerControllerType, dragging: Boolean) {
    fastScrollerDragStateMap[fastScrollerControllerType] = dragging
  }

  fun isDraggingFastScroller(fastScrollerControllerType: FastScroller.FastScrollerControllerType): Boolean {
    return fastScrollerDragStateMap[fastScrollerControllerType] ?: false
  }

  fun updateIsReplyLayoutOpened(threadControllerType: ThreadSlideController.ThreadControllerType, isOpened: Boolean) {
    replyLayoutOpenStateMap[threadControllerType] = isOpened
  }

  fun isReplyLayoutOpened(threadControllerType: ThreadSlideController.ThreadControllerType): Boolean {
    return replyLayoutOpenStateMap[threadControllerType] ?: false
  }

}