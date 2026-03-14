package com.github.k1rakishou.chan.utils

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.parameters.RecyclerIndexAndTopInfo

object RecyclerUtils {
  private const val TAG = "RecyclerUtils"

  @JvmStatic
  fun clearRecyclerCache(recyclerView: RecyclerView?) {
    try {
      val field = RecyclerView::class.java.getDeclaredField("mRecycler")
      field.isAccessible = true
      val recycler = field[recyclerView] as Recycler
      recycler.clear()
    } catch (error: Exception) {
      Logger.e(TAG, "Error clearing RecyclerView cache with reflection, error=${error.errorMessageOrClassName()}")
    }
  }

  /**
   * If [useTopMostChild] is false then the bottom-most visible child will be used
   * */
  @JvmStatic
  fun getIndexAndTop(recyclerView: RecyclerView, useTopMostChild: Boolean = true): RecyclerIndexAndTopInfo.IndexAndTop {
    var index = 0
    var top = 0

    val layoutManager = recyclerView.layoutManager
      ?: return RecyclerIndexAndTopInfo.IndexAndTop(index, top)

    if (layoutManager.childCount > 0) {
      if (useTopMostChild) {
        val topChild = layoutManager.getChildAt(0)
          ?: return RecyclerIndexAndTopInfo.IndexAndTop(index, top)

        index = (topChild.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        val params = topChild.layoutParams as RecyclerView.LayoutParams
        top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.paddingTop
      } else {
        val bottomChild = layoutManager.getChildAt(layoutManager.childCount - 1)
          ?: return RecyclerIndexAndTopInfo.IndexAndTop(index, top)

        index = (bottomChild.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        val params = bottomChild.layoutParams as RecyclerView.LayoutParams
        top = layoutManager.getDecoratedTop(bottomChild) - params.topMargin - recyclerView.paddingTop
      }
    }

    return RecyclerIndexAndTopInfo.IndexAndTop(index, top)
  }

  @JvmStatic
  fun RecyclerView.restoreScrollPosition(indexAndTop: RecyclerIndexAndTopInfo.IndexAndTop?) {
    if (indexAndTop == null) {
      return
    }

    val itemsCount = (adapter?.itemCount?.minus(1) ?: -1)
    if (itemsCount <= 0) {
      return
    }

    val newIndex = indexAndTop.index.coerceIn(0, itemsCount)

    when (val layoutManager = this.layoutManager) {
      is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(newIndex, indexAndTop.top)
      is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(newIndex, indexAndTop.top)
      is StaggeredGridLayoutManager -> layoutManager.scrollToPositionWithOffset(newIndex, indexAndTop.top)
    }
  }

  @JvmStatic
  fun RecyclerView.doOnRecyclerScrollStopped(func: (RecyclerView) -> Unit): RecyclerScrollCallbackDisposable {
    val listener = object : RecyclerView.OnScrollListener() {
      override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)

        if (newState != RecyclerView.SCROLL_STATE_IDLE) {
          return
        }

        func(recyclerView)
      }

    }

    val recyclerScrollCallbackDisposable = RecyclerScrollCallbackDisposable {
      removeOnScrollListener(listener)
    }

    addOnScrollListener(listener)

    return recyclerScrollCallbackDisposable
  }

  class RecyclerScrollCallbackDisposable(var disposableFunc: (() -> Unit)?) {

    fun dispose() {
      disposableFunc?.invoke()
      disposableFunc = null
    }

  }

}