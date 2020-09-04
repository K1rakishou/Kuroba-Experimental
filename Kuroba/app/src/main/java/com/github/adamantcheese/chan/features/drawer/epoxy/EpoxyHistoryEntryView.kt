package com.github.adamantcheese.chan.features.drawer.epoxy

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
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
import com.github.adamantcheese.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import java.lang.ref.WeakReference
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyHistoryEntryView @JvmOverloads constructor(
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
  private var descriptor: ChanDescriptor? = null

  private val viewHolder: LinearLayout
  private val thumbnailImage: AppCompatImageView
  private val title: AppCompatTextView
  private val bookmarkStats: AppCompatTextView
  private val imageSize: Int

  init {
    inflate(context, R.layout.epoxy_history_entry_view, this)
    inject(this)

    viewHolder = findViewById(R.id.history_entry_view_holder)
    thumbnailImage = findViewById(R.id.history_entry_image)
    title = findViewById(R.id.history_entry_title)
    bookmarkStats = findViewById(R.id.history_entry_bookmark_stats)

    imageSize = context.resources.getDimension(R.dimen.history_entry_image_size).toInt()
  }

  @ModelProp(ModelProp.Option.DoNotHash)
  fun setImageLoaderRequestData(imageLoaderRequestData: ImageLoaderRequestData?) {
    this.imageLoaderRequestData = imageLoaderRequestData
  }

  @ModelProp
  fun setTitle(titleText: String) {
    title.text = titleText
  }

  @ModelProp(options = [ModelProp.Option.DoNotHash])
  fun setDescriptor(descriptor: ChanDescriptor) {
    this.descriptor = descriptor
  }

  @ModelProp
  fun bindNavHistoryBookmarkAdditionalInfo(additionalInfo: NavHistoryBookmarkAdditionalInfo?) {
    if (additionalInfo == null) {
      bookmarkStats.visibility = View.GONE
      return
    }

    bookmarkStats.visibility = View.VISIBLE
    bookmarkStats.text = additionalInfo.newPosts.toString()

    if (additionalInfo.newQuotes > 0) {
      bookmarkStats.setTextColor(themeHelper.theme.pinPostsHasRepliesColor)
    } else if (!additionalInfo.watching) {
      bookmarkStats.setTextColor(themeHelper.theme.pinPostsNotWatchingColor)
    } else {
      bookmarkStats.setTextColor(themeHelper.theme.pinPostsNormalColor)
    }

    bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.NORMAL)
    bookmarkStats.paintFlags = Paint.ANTI_ALIAS_FLAG

    if (additionalInfo.isBumpLimit && additionalInfo.isImageLimit) {
      bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.BOLD_ITALIC)
    } else if (additionalInfo.isBumpLimit) {
      bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.ITALIC)
    } else if (additionalInfo.isImageLimit) {
      bookmarkStats.setTypeface(bookmarkStats.typeface, Typeface.BOLD)
    }

    if (additionalInfo.isLastPage) {
      bookmarkStats.paintFlags = bookmarkStats.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }
  }

  @CallbackProp
  fun clickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      viewHolder.setOnClickListener(null)
    } else {
      viewHolder.setOnClickListener { listener.invoke() }
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
    val thumbnailImageRef = WeakReference(thumbnailImage)

    requestDisposable = imageLoaderV2.loadFromNetwork(
      context,
      url.toString(),
      imageSize,
      imageSize,
      listOf(CIRCLE_CROP),
      { drawable -> thumbnailImageRef.get()?.setImageBitmap(drawable.bitmap) }
    )
  }

  private fun disposeRequest() {
    requestDisposable?.dispose()
    requestDisposable = null
  }

  data class ImageLoaderRequestData(val url: HttpUrl)

  companion object {
    private val CIRCLE_CROP = CircleCropTransformation()
  }
}