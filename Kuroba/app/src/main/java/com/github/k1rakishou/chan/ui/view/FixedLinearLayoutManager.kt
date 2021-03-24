package com.github.k1rakishou.chan.ui.view

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * This class hopefully finally fixes the bug where the recycler view automatically scrolls to top
 * after selecting a post text and then pressing "Scroll to bottom" in the thread's menu. This
 * happened because recycler view does this automatically. We don't need that so to disable it we
 * need to clear focus for every child in the recycler before the LayoutManager lays out children.
 * */
open class FixedLinearLayoutManager(
  private val recyclerView: RecyclerView
) : LinearLayoutManager(recyclerView.context) {

  override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State?) {
    val childCount = recyclerView.childCount
    for (index in 0 until childCount) {
      val childView = recyclerView.getChildAt(index)

      childView.clearFocus()
      recyclerView.clearChildFocus(childView)
    }

    super.onLayoutChildren(recycler, state)
  }

}