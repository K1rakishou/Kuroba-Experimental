package com.github.k1rakishou.chan.ui.globalstate

interface FastScrollerGlobalStateReadable {
  fun isDraggingFastScroller(fastScrollerControllerType: FastScrollerControllerType): Boolean
}

interface FastScrollerGlobalStateWriteable {
  fun updateIsDraggingFastScroller(fastScrollerControllerType: FastScrollerControllerType, dragging: Boolean)
}

class FastScrollerGlobalState : FastScrollerGlobalStateReadable, FastScrollerGlobalStateWriteable {
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
