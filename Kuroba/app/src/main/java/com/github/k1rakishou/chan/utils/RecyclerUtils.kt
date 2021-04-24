/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.utils

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.IndexAndTop

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
  fun getIndexAndTop(recyclerView: RecyclerView, useTopMostChild: Boolean = true): IndexAndTop {
    var index = 0
    var top = 0

    val layoutManager = recyclerView.layoutManager
      ?: return IndexAndTop(index, top)

    if (layoutManager.childCount > 0) {
      if (useTopMostChild) {
        val topChild = layoutManager.getChildAt(0)
          ?: return IndexAndTop(index, top)

        index = (topChild.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        val params = topChild.layoutParams as RecyclerView.LayoutParams
        top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.paddingTop
      } else {
        val bottomChild = layoutManager.getChildAt(layoutManager.childCount - 1)
          ?: return IndexAndTop(index, top)

        index = (bottomChild.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        val params = bottomChild.layoutParams as RecyclerView.LayoutParams
        top = layoutManager.getDecoratedTop(bottomChild) - params.topMargin - recyclerView.paddingTop
      }
    }

    return IndexAndTop(index, top)
  }

  @JvmStatic
  fun RecyclerView.restoreScrollPosition(indexAndTop: IndexAndTop?) {
    if (indexAndTop == null) {
      return
    }

    val itemsCount = (adapter?.itemCount?.minus(1) ?: -1)
    if (itemsCount <= 0) {
      return
    }

    val recyclerHeight = if (height > 0) {
      height - 1
    } else {
      height
    }

    val newIndex = indexAndTop.index.coerceIn(0, itemsCount)
    val newTop = indexAndTop.top.coerceIn(0, recyclerHeight)

    when (val layoutManager = this.layoutManager) {
      is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(newIndex, newTop)
      is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(newIndex, newTop)
      is StaggeredGridLayoutManager -> layoutManager.scrollToPositionWithOffset(newIndex, newTop)
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