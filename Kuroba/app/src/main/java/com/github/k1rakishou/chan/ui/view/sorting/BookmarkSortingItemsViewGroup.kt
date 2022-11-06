package com.github.k1rakishou.chan.ui.view.sorting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingLinearLayout
import com.github.k1rakishou.common.findChildren

class BookmarkSortingItemsViewGroup @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : TouchBlockingLinearLayout(context, attributeSet, defStyleAttr),
  View.OnClickListener {

  init {
    orientation = LinearLayout.VERTICAL

    require(childCount == 0) { "Bad child count: ${childCount}" }
    val bookmarksSortOrder = ChanSettings.bookmarksSortOrder.get()

    repeat(SORT_SETTINGS_COUNT) { index ->
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

  fun getCurrentSortingOrder(): ChanSettings.BookmarksSortOrder {
    val sortingItemViews = findChildren<BookmarkSortingItemView> { child -> child is BookmarkSortingItemView }
    if (sortingItemViews.isEmpty()) {
      return ChanSettings.BookmarksSortOrder.defaultOrder()
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
          ChanSettings.BookmarksSortOrder.CreatedOnDescending
        } else {
          ChanSettings.BookmarksSortOrder.CreatedOnAscending
        }
      }
      BookmarkSortingItemView.THREAD_ID_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          ChanSettings.BookmarksSortOrder.ThreadIdDescending
        } else {
          ChanSettings.BookmarksSortOrder.ThreadIdAscending
        }
      }
      BookmarkSortingItemView.UNREAD_REPLIES_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          ChanSettings.BookmarksSortOrder.UnreadRepliesDescending
        } else {
          ChanSettings.BookmarksSortOrder.UnreadRepliesAscending
        }
      }
      BookmarkSortingItemView.UNREAD_POSTS_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          ChanSettings.BookmarksSortOrder.UnreadPostsDescending
        } else {
          ChanSettings.BookmarksSortOrder.UnreadPostsAscending
        }
      }
      BookmarkSortingItemView.CUSTOM_SORT_ITEM_VIEW_TAG -> {
        if (sortDirectionDesc) {
          ChanSettings.BookmarksSortOrder.CustomDescending
        } else {
          ChanSettings.BookmarksSortOrder.CustomAscending
        }
      }
      else -> throw IllegalStateException("Unknown tag: ${sortingItemView.tag}")
    }
  }

  companion object {
    private const val SORT_SETTINGS_COUNT = 4
  }
}