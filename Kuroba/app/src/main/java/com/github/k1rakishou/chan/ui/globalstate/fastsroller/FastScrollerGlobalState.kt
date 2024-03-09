package com.github.k1rakishou.chan.ui.globalstate.fastsroller

interface IFastScrollerGlobalState {
  interface Readable {
    fun isDraggingFastScroller(fastScrollerControllerType: FastScrollerControllerType): Boolean
  }

  interface Writeable {
    fun updateIsDraggingFastScroller(fastScrollerControllerType: FastScrollerControllerType, dragging: Boolean)
  }
}

internal class FastScrollerGlobalState : IFastScrollerGlobalState.Readable, IFastScrollerGlobalState.Writeable {
  private val fastScrollerDragStateMap = mutableMapOf<FastScrollerControllerType, Boolean>()

  override fun isDraggingFastScroller(fastScrollerControllerType: FastScrollerControllerType): Boolean {
    return fastScrollerDragStateMap[fastScrollerControllerType] ?: false
  }

  override fun updateIsDraggingFastScroller(fastScrollerControllerType: FastScrollerControllerType, dragging: Boolean) {
    fastScrollerDragStateMap[fastScrollerControllerType] = dragging
  }

}

enum class FastScrollerControllerType {
  Catalog,
  Thread,
  Bookmarks,
  Album
}
