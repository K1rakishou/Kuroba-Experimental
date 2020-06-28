package com.github.adamantcheese.chan.features.bookmarks.epoxy

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
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

  init {
    Chan.inject(this)
  }

  override fun bindView(itemView: View) {
    viewHolder = itemView.findViewById(R.id.thread_bookmark_view_holder)
    bookmarkImage = itemView.findViewById(R.id.thread_bookmark_image)
    bookmarkTitle = itemView.findViewById(R.id.thread_bookmark_title)
    bookmarkStats = itemView.findViewById(R.id.thread_bookmark_stats)
  }

  fun unbind() {
    this.clickListener(null)

    this.imageLoaderRequestData = null
    this.threadDescriptor = null
    this.bookmarkImage.setImageBitmap(null)

    this.requestDisposable?.dispose()
    this.requestDisposable = null
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

  fun setThreadBookmarkStats(threadBookmarkStats: ThreadBookmarkStats?) {
    if (threadBookmarkStats == null) {
      bookmarkStats.visibility = View.GONE
      return
    }

    if (!threadBookmarkStats.showBookmarkStats) {
      bookmarkStats.visibility = View.GONE
      return
    }

    bookmarkStats.visibility = View.VISIBLE

    bookmarkStats.text = buildString {
      if (threadBookmarkStats.isFirstFetch) {
        append("Loading...")
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

    if (threadBookmarkStats.isBumpLimit && threadBookmarkStats.isImageLimit) {
      bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.BOLD_ITALIC)
    } else if (threadBookmarkStats.isBumpLimit) {
      bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.ITALIC)
    } else if (threadBookmarkStats.isImageLimit) {
      bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.BOLD)
    }

    if (threadBookmarkStats.isOnLastPage) {
      bookmarkStats.paintFlags = bookmarkStats.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }
  }

  fun clickListener(func: ((ChanDescriptor.ThreadDescriptor) -> Unit)?) {
    if (func == null) {
      viewHolder.setOnClickListener(null)
    } else {
      viewHolder.setOnClickListener { threadDescriptor?.let { func.invoke(it) } }
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
      object : ImageLoaderV2.SimpleImageListener {
        override fun onResponse(drawable: BitmapDrawable) {
          thumbnailImageRef.get()?.setImageBitmap(drawable.bitmap)
        }
      })
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
  }

}