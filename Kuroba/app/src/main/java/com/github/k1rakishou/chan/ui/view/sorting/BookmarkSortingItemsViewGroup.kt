package com.github.k1rakishou.chan.ui.view.sorting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingLinearLayout
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.findChildren
import com.github.k1rakishou.v2.parameters.BookmarksSortOrder

class BookmarkSortingItemsViewGroup @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : TouchBlockingLinearLayout(context, attributeSet, defStyleAttr),
  View.OnClickListener {

  init {
    orientation = LinearLayout.VERTICAL
    val kurobaSettings = appDependencies().kurobaSettings

    require(childCount == 0) { "Bad child count: ${childCount}" }
    val bookmarksSortOrder = kurobaSettings.application.bookmarksSortOrder.readBlocking()

    val sortSettingsCount = BookmarksSortOrder.entries.size / 2
    repeat(sortSettingsCount) { index ->
      val sortingItemView = BookmarkSortingItemView(context)
      sortingItemView.init(index, bookmarksSortOrder)
      sortingItemView.setOnClickListener(this)

      addView(sortingItemView)
    }
  }

  override fun onClick(v: View?) {
    if (v == null) {
      return
    }

    if (v !is BookmarkSortingItemView) {
      return
    }

    val clickedSortingItemView = v as? BookmarkSortingItemView
      ?: return

    val sortingItemViews = findChildren<BookmarkSortingItemView> { child -> child is BookmarkSortingItemView }

    sortingItemViews.forEach { sortingItemView ->
      if (sortingItemView === clickedSortingItemView) {
        sortingItemView.toggleSortDirection()
      } else {
        sortingItemView.clearSortDirection()
      }
    }
  }

  fun getCurrentSortingOrder(): BookmarksSortOrder {
    val sortingItemViews = findChildren<BookmarkSortingItemView> { child -> child is BookmarkSortingItemView }
    if (sortingItemViews.isEmpty()) {
      return BookmarksSortOrder.defaultOrder()
    }

    val sortDirectionDescList = sortingItemViews.map { sortingItemView -> sortingItemView.sortDirectionDesc }

    val nullCount = sortDirectionDescList.count { sortDirectionDesc -> sortDirectionDesc == null }
    check(nullCount == sortDirectionDescList.size - 1)

    val sortingItemView = sortingItemViews.firstOrNull { sortingItemView ->
      val sortDirectionDesc = sortingItemView.sortDirectionDesc
      if (sortDirectionDesc == null) {
        return@firstOrNull false
      }

      return@firstOrNull true
    }

    requireNotNull(sortingItemView) { "sortingItemView is null!" }
    val sortDirectionDesc = requireNotNull(sortingItemView.sortDirectionDesc) { "sortDirectionDesc is null" }

    return when (sortingItemView.tag) {
      BookmarkSortingItemView.BOOKMARK_CREATION_TIME_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          BookmarksSortOrder.CreatedOnDescending
        } else {
          BookmarksSortOrder.CreatedOnAscending
        }
      }
      BookmarkSortingItemView.THREAD_ID_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          BookmarksSortOrder.ThreadIdDescending
        } else {
          BookmarksSortOrder.ThreadIdAscending
        }
      }
      BookmarkSortingItemView.UNREAD_REPLIES_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          BookmarksSortOrder.UnreadRepliesDescending
        } else {
          BookmarksSortOrder.UnreadRepliesAscending
        }
      }
      BookmarkSortingItemView.UNREAD_POSTS_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          BookmarksSortOrder.UnreadPostsDescending
        } else {
          BookmarksSortOrder.UnreadPostsAscending
        }
      }
      BookmarkSortingItemView.CUSTOM_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          BookmarksSortOrder.CustomDescending
        } else {
          BookmarksSortOrder.CustomAscending
        }
      }
      else -> throw IllegalStateException("Unknown tag: ${sortingItemView.tag}")
    }
  }
}