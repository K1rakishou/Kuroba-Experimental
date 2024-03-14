package com.github.k1rakishou.chan.ui.globalstate.reply

import androidx.compose.ui.geometry.Offset
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface IReplyLayoutGlobalState {
  interface Readable {
    val replyLayoutVisibilityEventsFlow: Flow<ReplyLayoutVisibilityStates>
    val replyLayoutsBoundsFlow: Flow<ReplyLayoutBoundsStates>

    fun state(threadControllerType: ThreadControllerType): IIndividualReplyLayoutGlobalState.Readable
    fun allReplyLayoutCollapsed(): Boolean
    fun isAnyReplyLayoutExpanded(): Boolean
    fun anyReplyLayoutBoundsContainTouchPosition(touchPosition: Offset): Boolean
  }

  interface Writable {
    fun update(threadControllerType: ThreadControllerType, updater: (IIndividualReplyLayoutGlobalState.Writable) -> Unit)
  }
}

internal class ReplyLayoutGlobalState : IReplyLayoutGlobalState.Readable, IReplyLayoutGlobalState.Writable {
  private val catalogReplyLayoutState = IndividualReplyLayoutGlobalState(isCatalog = true)
  private val threadReplyLayoutState = IndividualReplyLayoutGlobalState(isCatalog = false)

  override val replyLayoutVisibilityEventsFlow: Flow<ReplyLayoutVisibilityStates>
    get() {
      return combine(
        flow = catalogReplyLayoutState.layoutVisibility,
        flow2 = threadReplyLayoutState.layoutVisibility,
        transform = { catalog, thread -> ReplyLayoutVisibilityStates(catalog = catalog, thread = thread) }
      )
    }

  override val replyLayoutsBoundsFlow: Flow<ReplyLayoutBoundsStates>
    get() {
      return combine(
        flow = catalogReplyLayoutState.boundsInRoot,
        flow2 = threadReplyLayoutState.boundsInRoot,
        transform = { catalog, thread ->
          return@combine ReplyLayoutBoundsStates(
            catalog = catalog,
            thread = thread
          )
        }
      )
    }

  override fun state(threadControllerType: ThreadControllerType): IIndividualReplyLayoutGlobalState.Readable {
    return when (threadControllerType) {
      ThreadControllerType.Catalog -> catalogReplyLayoutState
      ThreadControllerType.Thread -> threadReplyLayoutState
    }
  }

  override fun update(threadControllerType: ThreadControllerType, updater: (IIndividualReplyLayoutGlobalState.Writable) -> Unit) {
    when (threadControllerType) {
      ThreadControllerType.Catalog -> updater(catalogReplyLayoutState)
      ThreadControllerType.Thread -> updater(threadReplyLayoutState)
    }
  }

  override fun allReplyLayoutCollapsed(): Boolean {
    return catalogReplyLayoutState.isCollapsed() && threadReplyLayoutState.isCollapsed()
  }

  override fun isAnyReplyLayoutExpanded(): Boolean {
    return catalogReplyLayoutState.isExpanded() || threadReplyLayoutState.isExpanded()
  }

  override fun anyReplyLayoutBoundsContainTouchPosition(touchPosition: Offset): Boolean {
    return catalogReplyLayoutState.boundsContainTouchPosition(touchPosition) ||
      threadReplyLayoutState.boundsContainTouchPosition(touchPosition)
  }
}