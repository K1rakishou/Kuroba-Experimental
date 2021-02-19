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

  @JvmStatic
  fun getIndexAndTop(recyclerView: RecyclerView): IndexAndTop {
    var index = 0
    var top = 0

    val layoutManager = recyclerView.layoutManager
      ?: return IndexAndTop(index, top)

    if (layoutManager.childCount > 0) {
      val topChild = layoutManager.getChildAt(0)
        ?: return IndexAndTop(index, top)

      index = (topChild.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
      val params = topChild.layoutParams as RecyclerView.LayoutParams
      top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.paddingTop
    }

    return IndexAndTop(index, top)
  }

}