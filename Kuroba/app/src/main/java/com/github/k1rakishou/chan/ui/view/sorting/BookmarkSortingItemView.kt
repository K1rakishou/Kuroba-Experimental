package com.github.k1rakishou.chan.ui.view.sorting

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import javax.inject.Inject

class BookmarkSortingItemView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attributeSet, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var imageView: AppCompatImageView

  var sortDirectionDesc: Boolean? = null
    private set

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }

    orientation = LinearLayout.HORIZONTAL

    layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.WRAP_CONTENT
    )
    isClickable = true
    isFocusable = true
    AndroidUtils.setRippleBackground(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    imageView.drawable?.let { prevDrawable ->
      val isDarkColor = isDarkColor(themeEngine.chanTheme.backColor)
      imageView.setImageDrawable(themeEngine.tintDrawable(prevDrawable, isDarkColor))
    }
  }

  fun init(index: Int, bookmarksSortOrder: ChanSettings.BookmarksSortOrder) {
    tag = indexToTag(index)

    val image = AppCompatImageView(context)
    imageView = image

    image.tag = IMAGE_VIEW_TAG
    image.layoutParams = LayoutParams(IMAGE_SIZE, IMAGE_SIZE)
    image.updatePaddings(IMAGE_PADDING, IMAGE_PADDING, IMAGE_PADDING, IMAGE_PADDING)

    if (sortOrderToIndex(bookmarksSortOrder) == index) {
      setSortDirection(!bookmarksSortOrder.isAscending)
    } else {
      clearSortDirection()
    }

    val text = ColorizableTextView(context)
    text.layoutParams = LayoutParams(
      0,
      LayoutParams.WRAP_CONTENT,
      1f
    ).apply { gravity = Gravity.CENTER_VERTICAL }
    text.updateMargins(start = TEXT_MARGIN, end = TEXT_MARGIN)
    text.setText(indexToTextResId(index))
    text.textSize = TEXT_SIZE_SP

    addView(image)
    addView(text)

    onThemeChanged()
  }

  fun setSortDirection(isDescDirection: Boolean) {
    this.sortDirectionDesc = isDescDirection

    updateImageDrawable()
    updateImageTint()
  }

  fun toggleSortDirection() {
    if (sortDirectionDesc == null) {
      sortDirectionDesc = true
    } else {
      sortDirectionDesc = !sortDirectionDesc!!
    }

    updateImageDrawable()
    updateImageTint()
  }

  fun clearSortDirection() {
    sortDirectionDesc = null

    updateImageDrawable()
    updateImageTint()
  }

  private fun updateImageDrawable() {
    val drawable = when (sortDirectionDesc) {
      true -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_south_24)
      false -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_north_24)
      null -> null
    }

    imageView.setImageDrawable(drawable)
  }

  private fun updateImageTint() {
    imageView.drawable?.let { prevDrawable ->
      val isDarkColor = isDarkColor(themeEngine.chanTheme.backColor)
      imageView.setImageDrawable(themeEngine.tintDrawable(prevDrawable, isDarkColor))
    }
  }

  private fun indexToTag(index: Int): String {
    return when (index) {
      0 -> BOOKMARK_CREATION_TIME_SORT_ITEM_VIEW_TAG
      1 -> THREAD_ID_SORT_ITEM_VIEW_TAG
      2 -> UNREAD_REPLIES_SORT_ITEM_VIEW_TAG
      3 -> UNREAD_POSTS_SORT_ITEM_VIEW_TAG
      4 -> CUSTOM_SORT_ITEM_VIEW_TAG
      else -> throw IllegalAccessException("Unknown index: $index")
    }
  }

  private fun indexToTextResId(index: Int): Int {
    return when (index) {
      0 -> R.string.controller_bookmarks_sorting_by_bookmark_creation_time
      1 -> R.string.controller_bookmarks_sorting_by_thread_id
      2 -> R.string.controller_bookmarks_sorting_by_unread_replies
      3 -> R.string.controller_bookmarks_sorting_by_unread_posts
      4 -> R.string.controller_bookmarks_sorting_custom
      else -> throw IllegalAccessException("Unknown index: $index")
    }
  }

  private fun sortOrderToIndex(bookmarksSortOrder: ChanSettings.BookmarksSortOrder): Int {
    return when (bookmarksSortOrder) {
      ChanSettings.BookmarksSortOrder.CreatedOnAscending,
      ChanSettings.BookmarksSortOrder.CreatedOnDescending -> 0
      ChanSettings.BookmarksSortOrder.ThreadIdAscending,
      ChanSettings.BookmarksSortOrder.ThreadIdDescending -> 1
      ChanSettings.BookmarksSortOrder.UnreadRepliesAscending,
      ChanSettings.BookmarksSortOrder.UnreadRepliesDescending -> 2
      ChanSettings.BookmarksSortOrder.UnreadPostsAscending,
      ChanSettings.BookmarksSortOrder.UnreadPostsDescending -> 3
      ChanSettings.BookmarksSortOrder.CustomAscending,
      ChanSettings.BookmarksSortOrder.CustomDescending -> 4
    }
  }

  companion object {
    const val BOOKMARK_CREATION_TIME_SORT_ITEM_VIEW_TAG = "sort_by_bookmark_creation_time"
    const val THREAD_ID_SORT_ITEM_VIEW_TAG = "sort_by_thread_id"
    const val UNREAD_REPLIES_SORT_ITEM_VIEW_TAG = "sort_by_unread_replies"
    const val UNREAD_POSTS_SORT_ITEM_VIEW_TAG = "sort_by_unread_posts"
    const val CUSTOM_SORT_ITEM_VIEW_TAG = "sort_custom"

    const val IMAGE_VIEW_TAG = "sort_direction_image_view"

    private const val TEXT_SIZE_SP = 16f

    private val IMAGE_SIZE = dp(32f)
    private val IMAGE_PADDING = dp(4f)
    private val TEXT_MARGIN = dp(8f)
  }

}