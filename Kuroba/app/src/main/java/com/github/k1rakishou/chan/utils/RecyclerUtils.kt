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

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler

object RecyclerUtils {
  private const val TAG = "RecyclerUtils"

  @JvmStatic
  fun clearRecyclerCache(recyclerView: RecyclerView?) {
    try {
      val field = RecyclerView::class.java.getDeclaredField("mRecycler")
      field.isAccessible = true
      val recycler = field[recyclerView] as Recycler
      recycler.clear()
    } catch (e: Exception) {
      Logger.e(TAG, "Error clearing RecyclerView cache with reflection", e)
    }
  }

  @JvmStatic
  fun getIndexAndTop(recyclerView: RecyclerView): IndexAndTop {
    var index = 0
    var top = 0

    if (recyclerView.layoutManager!!.childCount > 0) {
      val topChild = recyclerView.layoutManager!!.getChildAt(0)
      index = (topChild!!.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
      val params = topChild.layoutParams as RecyclerView.LayoutParams
      val layoutManager = recyclerView.layoutManager
      top = layoutManager!!.getDecoratedTop(topChild) - params.topMargin - recyclerView.paddingTop
    }

    return IndexAndTop(index, top)
  }

  class IndexAndTop(var index: Int = 0, var top: Int = 0)
}