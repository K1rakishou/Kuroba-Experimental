package com.github.adamantcheese.chan.features.bookmarks.epoxy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import coil.request.RequestDisposable
import coil.transform.CircleCropTransformation
import coil.transform.GrayscaleTransformation
import coil.transform.RoundedCornersTransformation
import coil.transform.Transformation
import com.airbnb.epoxy.EpoxyHolder
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.image.ImageLoaderV2
import com.github.adamantcheese.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.AndroidUtils.dp
import com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout
import com.github.adamantcheese.common.resetClickListener
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import java.lang.ref.WeakReference
import javax.inject.Inject

@Suppress("LeakingThis")
open class BaseThreadBookmarkViewHolder(
  private val imageSize: Int
) : EpoxyHolder() {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeHelper: ThemeHelper

  private var imageLoaderRequestData: ImageLoaderRequestData? = null
  private var requestDisposable: RequestDisposable? = null
  private var threadDescriptor: ChanDescriptor.ThreadDescriptor? = null

  private lateinit var viewHolder: LinearLayout
  private lateinit var bookmarkImage: AppCompatImageView
  private lateinit var bookmarkTitle: AppCompatTextView
  private lateinit var bookmarkStats: AppCompatTextView

  private var bookmarkAdditionalStats: AppCompatTextView? = null
  private var bookmarkStatsHolder: LinearLayout? = null

  init {
    Chan.inject(this)
  }

  override fun bindView(itemView: View) {
    viewHolder = itemView.findViewById(R.id.thread_bookmark_view_holder)
    bookmarkImage = itemView.findViewById(R.id.thread_bookmark_image)
    bookmarkTitle = itemView.findViewById(R.id.thread_bookmark_title)
    bookmarkStats = itemView.findViewById(R.id.thread_bookmark_stats)
    bookmarkAdditionalStats = itemView.findViewById(R.id.thread_bookmark_additional_stats)
    bookmarkStatsHolder = itemView.findViewById(R.id.thread_bookmark_stats_holder)
  }

  fun unbind() {
    this.viewHolder.resetClickListener()
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
      val accent = ColorStateList.valueOf(themeHelper.theme.accentColor.color)
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

  fun setTitle(titleText: String?) {
    bookmarkTitle.text = titleText
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

    if (!threadBookmarkStats.showBookmarkStats) {
      bookmarkStats.visibility = View.GONE
      bookmarkAdditionalStats?.visibility = View.GONE
      return
    }

    bookmarkStats.visibility = View.VISIBLE
    setRegularBookmarksStats(isGridMode, threadBookmarkStats)

    if (isGridMode && !threadBookmarkStats.isFirstFetch) {
      bookmarkAdditionalStats!!.visibility = View.VISIBLE

      if (threadBookmarkStats.watching) {
        bookmarkAdditionalStats!!.setTextColor(themeHelper.theme.pinPostsNormalColor)
      } else {
        bookmarkAdditionalStats!!.setTextColor(themeHelper.theme.pinPostsNotWatchingColor)
      }

      if (!setAdditionalBookmarkStats(threadBookmarkStats)) {
        bookmarkAdditionalStats!!.visibility = View.GONE
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
    }

    if (additionalStats.isBlank()) {
      return false
    }

    bookmarkAdditionalStats!!.text = additionalStats
    return true
  }

  private fun setArchiveBookmarksStats() {
    bookmarkStats.text = bookmarkStats.context.getString(R.string.controller_bookmarks_bookmark_of_archived_thread)
    bookmarkStats.setTextColor(themeHelper.theme.pinPostsNormalColor)
    bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.NORMAL)
    bookmarkStats.paintFlags = Paint.ANTI_ALIAS_FLAG
  }

  private fun setRegularBookmarksStats(isGridMode: Boolean, threadBookmarkStats: ThreadBookmarkStats) {
    val context = bookmarkStats.context

    bookmarkStats.text = buildString {
      if (threadBookmarkStats.isFirstFetch) {
        append(context.getString(R.string.controller_bookmarks_bookmark_loading))
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
    }

    if (threadBookmarkStats.newQuotes > 0) {
      bookmarkStats.setTextColor(themeHelper.theme.pinPostsHasRepliesColor)
    } else if (!threadBookmarkStats.watching) {
      bookmarkStats.setTextColor(themeHelper.theme.pinPostsNotWatchingColor)
    } else {
      bookmarkStats.setTextColor(themeHelper.theme.pinPostsNormalColor)
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
      viewHolder.resetClickListener()
    } else {
      viewHolder.setOnClickListener { threadDescriptor?.let { func.invoke(it) } }
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
    waitForLayout(bookmarkImage) {
      bindImageInternal(isGridMode, watching, context)
      return@waitForLayout true
    }
  }

  private fun bindImageInternal(isGridMode: Boolean, watching: Boolean, context: Context) {
    val url = imageLoaderRequestData?.url
    val thumbnailImageRef = WeakReference(bookmarkImage)

    val transformations: MutableList<Transformation> = if (isGridMode) {
      mutableListOf(ROUNDED_CORNERS)
    } else {
      mutableListOf(CIRCLE_CROP)
    }

    if (!watching) {
      transformations.add(GRAYSCALE)
    }

    requestDisposable = imageLoaderV2.loadFromNetwork(
      context,
      url.toString(),
      bookmarkImage.width,
      imageSize,
      transformations,
      { drawable -> thumbnailImageRef.get()?.setImageBitmap(drawable.bitmap) }
    )
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
  }

}