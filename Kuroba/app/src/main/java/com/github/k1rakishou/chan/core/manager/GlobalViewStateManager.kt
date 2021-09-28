package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.KurobaBottomNavigationView
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GlobalViewStateManager {
  private val fastScrollerDragStateMap = mutableMapOf<FastScroller.FastScrollerControllerType, Boolean>()
  private val replyLayoutOpenStateMap = mutableMapOf<ThreadSlideController.ThreadControllerType, Boolean>()

  private val replyLayoutOpenStateRelay = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

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

  fun listenForBottomNavViewSwipeUpGestures(): SharedFlow<Unit> {
    return replyLayoutOpenStateRelay.asSharedFlow()
  }

  fun onBottomNavViewSwipeUpGestureTriggered() {
    if (!KurobaBottomNavigationView.isBottomNavViewEnabled()) {
      return
    }

    replyLayoutOpenStateRelay.tryEmit(Unit)
  }

}