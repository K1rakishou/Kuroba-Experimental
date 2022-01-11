package com.github.k1rakishou.chan.features.bookmarks.epoxy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import coil.request.Disposable
import coil.size.Scale
import coil.transform.CircleCropTransformation
import coil.transform.GrayscaleTransformation
import coil.transform.RoundedCornersTransformation
import coil.transform.Transformation
import com.airbnb.epoxy.EpoxyHolder
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkSelection
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.k1rakishou.chan.ui.view.SelectionCheckView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.resetClickListener
import com.github.k1rakishou.common.resetLongClickListener
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import javax.inject.Inject

@Suppress("LeakingThis")
open class BaseThreadBookmarkViewHolder : EpoxyHolder() {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine

  private var imageLoaderRequestData: ImageLoaderRequestData? = null
  private var requestDisposable: Disposable? = null
  private var threadDescriptor: ChanDescriptor.ThreadDescriptor? = null
  private var threadBookmarkSelection: ThreadBookmarkSelection? = null

  private lateinit var viewRoot: FrameLayout
  private lateinit var viewHolder: LinearLayout
  private lateinit var bookmarkImage: AppCompatImageView
  private lateinit var bookmarkTitle: AppCompatTextView
  private lateinit var bookmarkStats: AppCompatTextView
  private lateinit var selectionCheckView: SelectionCheckView
  var dragIndicator: AppCompatImageView? = null

  private var bookmarkAdditionalStats: AppCompatTextView? = null
  private var bookmarkStatsHolder: LinearLayout? = null

  override fun bindView(itemView: View) {
    AppModuleAndroidUtils.extractActivityComponent(itemView.context)
      .inject(this)

    viewRoot = itemView.findViewById(R.id.thread_bookmark_view_root)
    viewHolder = itemView.findViewById(R.id.thread_bookmark_view_holder)
    bookmarkImage = itemView.findViewById(R.id.thread_bookmark_image)
    bookmarkTitle = itemView.findViewById(R.id.thread_bookmark_title)
    bookmarkStats = itemView.findViewById(R.id.thread_bookmark_stats)
    bookmarkAdditionalStats = itemView.findViewById(R.id.thread_bookmark_additional_stats)
    bookmarkStatsHolder = itemView.findViewById(R.id.thread_bookmark_stats_holder)
    selectionCheckView = itemView.findViewById(R.id.selection_check_view)

    dragIndicator = itemView.findViewById(R.id.bookmark_drag_indicator)
    dragIndicator!!.setVisibilityFast(View.VISIBLE)
  }

  fun unbind() {
    this.viewRoot.resetClickListener()
    this.bookmarkStatsHolder?.resetClickListener()
    this.bookmarkStats.resetClickListener()

    this.imageLoaderRequestData = null
    this.threadDescriptor = null
    this.bookmarkImage.setImageBitmap(null)

    this.requestDisposable?.dispose()
    this.requestDisposable = null
  }

  fun highlightBookmark(highlight: Boolean) {
    if (highlight) {
      val accent = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
        .withAlpha(HIGHLIGHT_COLOR_ALPHA)

      viewHolder.setBackgroundColor(accent.defaultColor)
    } else  {
      viewHolder.setBackgroundColor(DEFAULT_ROOT_VIEW_COLOR)
    }
  }

  fun setImageLoaderRequestData(imageLoaderRequestData: ImageLoaderRequestData?) {
    this.imageLoaderRequestData = imageLoaderRequestData
  }

  fun setDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor?) {
    this.threadDescriptor = threadDescriptor
  }

  fun setTitle(titleText: String?, isWatching: Boolean) {
    bookmarkTitle.text = titleText

    if (isWatching) {
      bookmarkTitle.setTextColor(themeEngine.chanTheme.textColorPrimary)
    } else {
      bookmarkTitle.setTextColor(themeEngine.chanTheme.bookmarkCounterNotWatchingColor)
    }
  }

  fun setThreadBookmarkStats(isGridMode: Boolean, threadBookmarkStats: ThreadBookmarkStats?) {
    if (threadBookmarkStats == null) {
      bookmarkStats.visibility = View.GONE
      return
    }

    if (threadBookmarkStats.isArchive) {
      bookmarkStats.visibility = View.VISIBLE
      bookmarkAdditionalStats?.visibility = View.GONE

      setArchiveBookmarksStats()
      return
    }

    if (!threadBookmarkStats.isWatcherEnabled) {
      bookmarkStats.visibility = View.VISIBLE
      bookmarkAdditionalStats?.visibility = View.GONE

      setWatcherDisabledStats()
      return
    }

    bookmarkStats.visibility = View.VISIBLE
    setRegularBookmarksStats(isGridMode, threadBookmarkStats)

    if (isGridMode && !threadBookmarkStats.isFirstFetch) {
      bookmarkAdditionalStats?.visibility = View.VISIBLE

      if (threadBookmarkStats.watching && threadBookmarkStats.isError) {
        bookmarkAdditionalStats?.setTextColor(themeEngine.chanTheme.errorColor)
      } else if (threadBookmarkStats.watching) {
        bookmarkAdditionalStats?.setTextColor(themeEngine.chanTheme.bookmarkCounterNormalColor)
      } else {
        bookmarkAdditionalStats?.setTextColor(themeEngine.chanTheme.bookmarkCounterNotWatchingColor)
      }

      if (!setAdditionalBookmarkStats(threadBookmarkStats)) {
        bookmarkAdditionalStats?.visibility = View.GONE
      }
    } else {
      bookmarkAdditionalStats?.visibility = View.GONE
    }
  }

  private fun setAdditionalBookmarkStats(threadBookmarkStats: ThreadBookmarkStats): Boolean {
    val additionalStats = buildString {
      if (threadBookmarkStats.totalPages > 0) {
        append("Pg: ")
        append(threadBookmarkStats.currentPage)
        append("/")
        append(threadBookmarkStats.totalPages)
      }

      if (threadBookmarkStats.isBumpLimit) {
        if (isNotEmpty()) {
          append(", ")
        }

        append("BL")
      }

      if (threadBookmarkStats.isImageLimit) {
        if (isNotEmpty()) {
          append(", ")
        }

        append("IL")
      }

      if (threadBookmarkStats.isError) {
        if (isNotEmpty()) {
          append(", ")
        }

        append("Err")
      }

      if (threadBookmarkStats.isFilterWatchBookmark) {
        if (isNotEmpty()) {
          append(", ")
        }

        append("FW")
      }

      if (threadBookmarkStats.isDownloading) {
        if (isNotEmpty()) {
          append(", ")
        }

        append("DL")
      }
    }

    if (additionalStats.isBlank()) {
      return false
    }

    bookmarkAdditionalStats?.text = additionalStats
    return true
  }

  private fun setWatcherDisabledStats() {
    bookmarkStats.text = bookmarkStats.context.getString(R.string.controller_bookmarks_watcher_disabled)
    bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterNormalColor)
    bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.NORMAL)
    bookmarkStats.paintFlags = Paint.ANTI_ALIAS_FLAG
  }

  private fun setArchiveBookmarksStats() {
    bookmarkStats.text = bookmarkStats.context.getString(R.string.controller_bookmarks_bookmark_of_archived_thread)
    bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterNormalColor)
    bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.NORMAL)
    bookmarkStats.paintFlags = Paint.ANTI_ALIAS_FLAG
  }

  private fun setRegularBookmarksStats(isGridMode: Boolean, threadBookmarkStats: ThreadBookmarkStats) {
    val context = bookmarkStats.context

    bookmarkStats.text = buildString {
      if (threadBookmarkStats.isFirstFetch) {
        append(context.getString(R.string.loading))
        return@buildString
      }

      append(threadBookmarkStats.totalPosts)
      append(" / ")
      append(threadBookmarkStats.newPosts)

      if (threadBookmarkStats.newQuotes > 0) {
        append(" (")
        append(threadBookmarkStats.newQuotes)
        append(")")
      }

      if (!isGridMode) {
        if (threadBookmarkStats.isFilterWatchBookmark) {
          if (isNotEmpty()) {
            append(" ")
          }

          append("FW")
        }

        if (threadBookmarkStats.isDownloading) {
          if (isNotEmpty()) {
            append(", ")
          }

          append("DL")
        }
      }
    }

    if (threadBookmarkStats.newQuotes > 0) {
      bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterHasRepliesColor)
    } else if (!threadBookmarkStats.watching) {
      bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterNotWatchingColor)
    } else {
      bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterNormalColor)
    }

    bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.NORMAL)
    bookmarkStats.paintFlags = Paint.ANTI_ALIAS_FLAG

    if (!isGridMode) {
      if (threadBookmarkStats.isBumpLimit && threadBookmarkStats.isImageLimit) {
        bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.BOLD_ITALIC)
      } else if (threadBookmarkStats.isBumpLimit) {
        bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.ITALIC)
      } else if (threadBookmarkStats.isImageLimit) {
        bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.BOLD)
      }

      if (threadBookmarkStats.isLastPage()) {
        bookmarkStats.paintFlags = bookmarkStats.paintFlags or Paint.UNDERLINE_TEXT_FLAG
      }
    }
  }

  fun bookmarkClickListener(func: ((ChanDescriptor.ThreadDescriptor) -> Unit)?) {
    if (func == null) {
      viewRoot.resetClickListener()
    } else {
      viewRoot.setOnClickListener { threadDescriptor?.let { func.invoke(it) } }
    }
  }

  fun bookmarkSelection(threadBookmarkSelection: ThreadBookmarkSelection?) {
    this.threadBookmarkSelection = threadBookmarkSelection

    if (threadBookmarkSelection == null) {
      selectionCheckView.setVisibilityFast(View.GONE)
      return
    }

    selectionCheckView.setVisibilityFast(View.VISIBLE)
    selectionCheckView.setChecked(threadBookmarkSelection.isSelected)
  }

  fun bookmarkLongClickListener(func: ((ChanDescriptor.ThreadDescriptor) -> Unit)?) {
    if (func == null) {
      viewRoot.resetLongClickListener()
      return
    }

    viewRoot.setOnLongClickListener {
      if (threadDescriptor == null) {
        return@setOnLongClickListener false
      }

      threadDescriptor?.let { func.invoke(it) }
      return@setOnLongClickListener true
    }
  }

  fun bookmarkStatsClickListener(isGridMode: Boolean, func: ((ChanDescriptor.ThreadDescriptor) -> Unit)?) {
    if (func == null) {
      if (isGridMode) {
        bookmarkStatsHolder!!.resetClickListener()
      } else {
        bookmarkStats.resetClickListener()
      }
    } else {
      if (isGridMode) {
        bookmarkStatsHolder!!.setOnClickListener { threadDescriptor?.let { func.invoke(it) } }
      } else {
        bookmarkStats.setOnClickListener { threadDescriptor?.let { func.invoke(it) } }
      }
    }
  }

  fun bindImage(isGridMode: Boolean, watching: Boolean, context: Context) {
    val url = imageLoaderRequestData?.url
    if (url == null) {
      requestDisposable?.dispose()
      requestDisposable = null

      requestDisposable = imageLoaderV2.loadFromResources(
        context,
        R.drawable.error_icon,
        ImageLoaderV2.ImageSize.MeasurableImageSize.create(bookmarkImage),
        Scale.FIT,
        emptyList(),
        { drawable -> bookmarkImage.setImageBitmap(drawable.bitmap) }
      )

      return
    }

    val transformations: MutableList<Transformation> = if (isGridMode) {
      mutableListOf(ROUNDED_CORNERS)
    } else {
      mutableListOf(CIRCLE_CROP)
    }

    if (!watching) {
      transformations.add(GRAYSCALE)
    }

    requestDisposable?.dispose()
    requestDisposable = null

    requestDisposable = imageLoaderV2.loadFromNetwork(
      context = context,
      url = url.toString(),
      cacheFileType = CacheFileType.BookmarkThumbnail,
      imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(bookmarkImage),
      transformations = transformations,
      listener = { drawable -> bookmarkImage.setImageBitmap(drawable.bitmap) }
    )
  }

  fun updateGridViewSizes(isTablet: Boolean) {
    if (isTablet) {
      bookmarkStats.textSize = 16f
      bookmarkAdditionalStats?.textSize = 16f
      bookmarkTitle.textSize = 16f
      bookmarkTitle.maxLines = 4
    } else {
      bookmarkStats.textSize = 12f
      bookmarkAdditionalStats?.textSize = 12f
      bookmarkTitle.textSize = 12f
      bookmarkTitle.maxLines = 2
    }
  }

  fun updateListViewSizes(isTablet: Boolean) {
    if (isTablet) {
      viewHolder.updatePaddings(
        BOOKMARK_HOLDER_TABLET_PADDING,
        BOOKMARK_HOLDER_END_PADDING,
        BOOKMARK_HOLDER_TABLET_PADDING,
        BOOKMARK_HOLDER_TABLET_PADDING
      )

      bookmarkTitle.textSize = 16f
      bookmarkStats.textSize = 16f
    } else {
      viewHolder.updatePaddings(
        BOOKMARK_HOLDER_NON_TABLET_PADDING,
        BOOKMARK_HOLDER_END_PADDING,
        BOOKMARK_HOLDER_NON_TABLET_PADDING,
        BOOKMARK_HOLDER_NON_TABLET_PADDING
      )

      bookmarkTitle.textSize = 12f
      bookmarkStats.textSize = 12f
    }
  }

  fun updateDragIndicatorState(
    reorderingMode: Boolean,
    threadBookmarkStats: ThreadBookmarkStats?
  ) {
    if (threadBookmarkStats == null) {
      dragIndicator?.setVisibilityFast(View.VISIBLE)
      return
    }

    if (!reorderingMode) {
      dragIndicator?.setVisibilityFast(View.GONE)
      return
    }

    if (ChanSettings.moveNotActiveBookmarksToBottom.get() && threadBookmarkStats.isDeadOrNotWatching()) {
      dragIndicator?.setVisibilityFast(View.GONE)
      return
    }

    if (ChanSettings.moveBookmarksWithUnreadRepliesToTop.get() && threadBookmarkStats.newQuotes > 0) {
      dragIndicator?.setVisibilityFast(View.GONE)
      return
    }

    dragIndicator?.setVisibilityFast(View.VISIBLE)
  }

  fun updateDragIndicatorColors(isGridMode: Boolean) {
    dragIndicator?.let { indicator ->
      if (indicator.visibility != View.VISIBLE) {
        return@let
      }

      if (isGridMode) {
        indicator.imageTintList = ColorStateList.valueOf(ThemeEngine.LIGHT_DRAWABLE_TINT)
      } else {
        val color = ThemeEngine.resolveDrawableTintColor(
          isDarkColor(themeEngine.chanTheme.backColor)
        )

        indicator.imageTintList = ColorStateList.valueOf(color)
      }
    }
  }

  data class ImageLoaderRequestData(val url: HttpUrl?)

  companion object {
    private val GRAYSCALE = GrayscaleTransformation()
    private val CIRCLE_CROP = CircleCropTransformation()
    private val ROUNDED_CORNERS = RoundedCornersTransformation(
      dp(1f).toFloat(),
      dp(1f).toFloat(),
      dp(1f).toFloat(),
      dp(1f).toFloat()
    )

    private const val DEFAULT_ROOT_VIEW_COLOR = Color.TRANSPARENT
    private const val HIGHLIGHT_COLOR_ALPHA = 50

    private val BOOKMARK_HOLDER_TABLET_PADDING = dp(4f)
    private val BOOKMARK_HOLDER_NON_TABLET_PADDING = dp(2f)
    private val BOOKMARK_HOLDER_END_PADDING = dp(10f)
  }

}