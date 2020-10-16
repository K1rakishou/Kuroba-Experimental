package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.view.sorting.BookmarkSortingItemsViewGroup

class BookmarksSortingController(
  context: Context,
  private var sortingOrderChangeListener: SortingOrderChangeListener? = null
) : BaseFloatingController(context) {

  private lateinit var outsideArea: ConstraintLayout
  private lateinit var bookmarkSortingItemsViewGroup: BookmarkSortingItemsViewGroup

  private lateinit var cancel: ColorizableBarButton
  private lateinit var apply: ColorizableBarButton

  private var presenting = true

  override fun getLayoutId(): Int = R.layout.controller_bookmarks_sorting

  override fun onCreate() {
    super.onCreate()

    outsideArea = view.findViewById(R.id.outside_area)
    bookmarkSortingItemsViewGroup = view.findViewById(R.id.sorting_items_view_group)
    cancel = view.findViewById(R.id.cancel_button)
    apply = view.findViewById(R.id.apply_button)

    cancel.setOnClickListener { pop() }
    outsideArea.setOnClickListener { pop() }

    apply.setOnClickListener {
      val prevOrder = ChanSettings.bookmarksSortOrder.get()
      val newOrder = bookmarkSortingItemsViewGroup.getCurrentSortingOrder()

      if (prevOrder != newOrder) {
        ChanSettings.bookmarksSortOrder.set(newOrder)
        sortingOrderChangeListener?.onSortingOrderChanged()
      }

      pop()
    }
  }

  override fun onBack(): Boolean {
    if (presenting) {
      pop()
      return true
    }

    return super.onBack()
  }

  private fun pop() {
    if (!presenting) {
      return
    }

    presenting = false
    stopPresenting()
  }

  override fun onDestroy() {
    super.onDestroy()
    sortingOrderChangeListener = null
  }

  interface SortingOrderChangeListener {
    fun onSortingOrderChanged()
  }

}