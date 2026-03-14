package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBias
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.ui.view.sorting.BookmarkSortingItemsViewGroup

class BookmarksSortingController(
  context: Context,
  private val constraintLayoutBias: ConstraintLayoutBias,
  private var bookmarksView: BookmarksView? = null
) : BaseFloatingController(context) {

  private lateinit var outsideArea: ConstraintLayout
  private lateinit var bookmarkSortingItemsViewGroup: BookmarkSortingItemsViewGroup
  private lateinit var moveNotActiveBookmarksToBottom: ColorizableCheckBox
  private lateinit var moveBookmarksWithUnreadRepliesToTop: ColorizableCheckBox

  private lateinit var cancel: ColorizableBarButton
  private lateinit var apply: ColorizableBarButton

  override fun getLayoutId(): Int = R.layout.controller_bookmarks_sorting

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    outsideArea = view.findViewById(R.id.outside_area)
    bookmarkSortingItemsViewGroup = view.findViewById(R.id.sorting_items_view_group)
    moveNotActiveBookmarksToBottom = view.findViewById(R.id.move_not_active_bookmark_to_bottom)
    moveBookmarksWithUnreadRepliesToTop = view.findViewById(R.id.move_bookmarks_with_unread_replies_to_top)
    cancel = view.findViewById(R.id.cancel_button)
    apply = view.findViewById(R.id.apply_button)

    val innerContainer = view.findViewById<FrameLayout>(R.id.inner_container)

    innerContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
      horizontalBias = constraintLayoutBias.horizontalBias
      verticalBias = constraintLayoutBias.verticalBias
    }

    cancel.setOnClickListener { pop() }
    outsideArea.setOnClickListener { pop() }

    moveNotActiveBookmarksToBottom.isChecked =
      kurobaSettings.application.moveNotActiveBookmarksToBottom.readBlocking()
    moveBookmarksWithUnreadRepliesToTop.isChecked =
      kurobaSettings.application.moveBookmarksWithUnreadRepliesToTop.readBlocking()

    apply.setOnClickListener {
      var shouldReloadBookmarks = false

      shouldReloadBookmarks = shouldReloadBookmarks or updateBookmarkSortingSetting()
      shouldReloadBookmarks = shouldReloadBookmarks or updateMoveDeadBookmarksToBottomSetting()
      shouldReloadBookmarks = shouldReloadBookmarks or updateMoveBookmarksWithUnreadRepliesToTopSetting()

      if (shouldReloadBookmarks) {
        bookmarksView?.reloadBookmarks()
      }

      pop()
    }
  }

  private fun updateMoveBookmarksWithUnreadRepliesToTopSetting(): Boolean {
    val prevSettingValue = kurobaSettings.application.moveBookmarksWithUnreadRepliesToTop.readBlocking()
    val newSettingValue = moveBookmarksWithUnreadRepliesToTop.isChecked

    if (prevSettingValue == newSettingValue) {
      return false
    }

    kurobaSettings.application.moveBookmarksWithUnreadRepliesToTop.writeAsync(newSettingValue)
    return true
  }

  private fun updateMoveDeadBookmarksToBottomSetting(): Boolean {
    val prevSettingValue = kurobaSettings.application.moveNotActiveBookmarksToBottom.readBlocking()
    val newSettingValue = moveNotActiveBookmarksToBottom.isChecked

    if (prevSettingValue == newSettingValue) {
      return false
    }

    kurobaSettings.application.moveNotActiveBookmarksToBottom.writeAsync(newSettingValue)
    return true
  }

  private fun updateBookmarkSortingSetting(): Boolean {
    val prevOrder = kurobaSettings.application.bookmarksSortOrder.readBlocking()
    val newOrder = bookmarkSortingItemsViewGroup.getCurrentSortingOrder()

    if (prevOrder == newOrder) {
      return false
    }

    kurobaSettings.application.bookmarksSortOrder.writeAsync(newOrder)
    return true
  }

  override fun onDestroy() {
    super.onDestroy()
    bookmarksView = null
  }

}