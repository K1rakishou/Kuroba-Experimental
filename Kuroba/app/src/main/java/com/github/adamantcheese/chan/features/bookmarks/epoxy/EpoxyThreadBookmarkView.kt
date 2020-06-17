package com.github.adamantcheese.chan.features.bookmarks.epoxy

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import coil.request.RequestDisposable
import coil.transform.CircleCropTransformation
import com.airbnb.epoxy.*
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.image.ImageLoaderV2
import com.github.adamantcheese.chan.features.bookmarks.data.ThreadBookmarkStats
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import java.lang.ref.WeakReference
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyThreadBookmarkView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeHelper: ThemeHelper

  private var imageLoaderRequestData: ImageLoaderRequestData? = null
  private var requestDisposable: RequestDisposable? = null
  private var threadDescriptor: ChanDescriptor.ThreadDescriptor? = null

  private val viewHolder: LinearLayout
  private val bookmarkImage: AppCompatImageView
  private val bookmarkTitle: AppCompatTextView
  private val bookmarkStats: AppCompatTextView
  private val imageSize: Int

  init {
    inflate(context, R.layout.epoxy_thread_bookmark_view, this)
    inject(this)

    viewHolder = findViewById(R.id.thread_bookmark_view_holder)
    bookmarkImage = findViewById(R.id.thread_bookmark_image)
    bookmarkTitle = findViewById(R.id.thread_bookmark_title)
    bookmarkStats = findViewById(R.id.thread_bookmark_stats)
    imageSize = context.resources.getDimension(R.dimen.thread_bookmark_view_image_size).toInt()
  }

  @ModelProp(ModelProp.Option.DoNotHash)
  fun setImageLoaderRequestData(imageLoaderRequestData: ImageLoaderRequestData?) {
    this.imageLoaderRequestData = imageLoaderRequestData
  }

  @ModelProp(options = [ModelProp.Option.DoNotHash])
  fun setDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    this.threadDescriptor = threadDescriptor
  }

  @ModelProp
  fun setTitle(titleText: String) {
    bookmarkTitle.text = titleText
  }

  @ModelProp
  fun setThreadBookmarkStats(threadBookmarkStats: ThreadBookmarkStats) {
    if (!threadBookmarkStats.showBookmarkStats) {
      bookmarkStats.visibility = View.GONE
      return
    }

    bookmarkStats.visibility = View.VISIBLE

    bookmarkStats.text = buildString {
      append(threadBookmarkStats.newPosts)

      if (threadBookmarkStats.newQuotes > 0) {
        append(" (").append(threadBookmarkStats.newQuotes).append(")")
      }

      if (threadBookmarkStats.totalPosts > 0) {
        append(" / ").append(threadBookmarkStats.totalPosts)
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

    if (threadBookmarkStats.isLastPage) {
      bookmarkStats.paintFlags = bookmarkStats.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }
  }

  @CallbackProp
  fun clickListener(func: ((ChanDescriptor.ThreadDescriptor) -> Unit)?) {
    if (func == null) {
      viewHolder.setOnClickListener(null)
    } else {
      viewHolder.setOnClickListener { threadDescriptor?.let { func.invoke(it) } }
    }
  }

  @OnViewRecycled
  fun onRecycled() {
    disposeRequest()
    imageLoaderRequestData = null
  }

  @AfterPropsSet
  fun afterPropsSet() {
    val url = imageLoaderRequestData?.url
    val thumbnailImageRef = WeakReference(bookmarkImage)

    requestDisposable = imageLoaderV2.loadFromNetwork(
      context,
      url.toString(),
      imageSize,
      imageSize,
      listOf(CIRCLE_CROP),
      object : ImageLoaderV2.SimpleImageListener {
        override fun onResponse(drawable: BitmapDrawable) {
          thumbnailImageRef.get()?.setImageBitmap(drawable.bitmap)
        }
      })
  }

  private fun disposeRequest() {
    requestDisposable?.dispose()
    requestDisposable = null
  }

  data class ImageLoaderRequestData(val url: HttpUrl?)

  companion object {
    private val CIRCLE_CROP = CircleCropTransformation()
  }
}