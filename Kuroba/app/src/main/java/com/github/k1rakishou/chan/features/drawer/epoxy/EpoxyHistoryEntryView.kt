package com.github.k1rakishou.chan.features.drawer.epoxy

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import coil.request.Disposable
import coil.transform.CircleCropTransformation
import com.airbnb.epoxy.*
import com.github.k1rakishou.chan.Chan.Companion.inject
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
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
  lateinit var themeEngine: ThemeEngine

  private var imagesLoaderRequestData: ImagesLoaderRequestData? = null
  private var threadImageRequestDisposable: Disposable? = null
  private var siteImageRequestDisposable: Disposable? = null
  private var descriptor: ChanDescriptor? = null

  private val viewHolder: LinearLayout
  private val threadThumbnailImage: AppCompatImageView
  private val siteThumbnailImage: AppCompatImageView
  private val title: AppCompatTextView
  private val bookmarkStats: AppCompatTextView
  private val threadThumbnailImageSize: Int
  private val siteThumbnailImageSize: Int

  init {
    inflate(context, R.layout.epoxy_history_entry_view, this)
    inject(this)

    viewHolder = findViewById(R.id.history_entry_view_holder)
    threadThumbnailImage = findViewById(R.id.history_entry_thread_image)
    siteThumbnailImage = findViewById(R.id.history_entry_site_image)
    title = findViewById(R.id.history_entry_title)
    bookmarkStats = findViewById(R.id.history_entry_bookmark_stats)

    threadThumbnailImageSize = context.resources.getDimension(R.dimen.history_entry_thread_image_size).toInt()
    siteThumbnailImageSize = context.resources.getDimension(R.dimen.history_entry_site_image_size).toInt()

    threadThumbnailImage.visibility = View.GONE
    siteThumbnailImage.visibility = View.GONE

    title.setTextColor(themeEngine.chanTheme.textPrimaryColor)
  }

  @ModelProp(ModelProp.Option.DoNotHash)
  fun setImageLoaderRequestData(imagesLoaderRequestData: ImagesLoaderRequestData?) {
    this.imagesLoaderRequestData = imagesLoaderRequestData
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
      bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterHasRepliesColor)
    } else if (!additionalInfo.watching) {
      bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterNotWatchingColor)
    } else {
      bookmarkStats.setTextColor(themeEngine.chanTheme.bookmarkCounterNormalColor)
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
    threadImageRequestDisposable?.dispose()
    threadImageRequestDisposable = null

    siteImageRequestDisposable?.dispose()
    siteImageRequestDisposable = null

    imagesLoaderRequestData = null

    threadThumbnailImage.setImageBitmap(null)
    siteThumbnailImage.setImageBitmap(null)
  }

  @AfterPropsSet
  fun afterPropsSet() {
    if (imagesLoaderRequestData == null) {
      return
    }

    val threadThumbnailUrl = imagesLoaderRequestData?.threadThumbnailUrl
    if (threadThumbnailUrl != null) {
      val threadThumbnailImageRef = WeakReference(threadThumbnailImage)

      threadImageRequestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        threadThumbnailUrl.toString(),
        threadThumbnailImageSize,
        threadThumbnailImageSize,
        listOf(CIRCLE_CROP),
        { drawable ->
          threadThumbnailImageRef.get()?.visibility = View.VISIBLE
          threadThumbnailImageRef.get()?.setImageBitmap(drawable.bitmap)
        }
      )
    }

    val siteThumbnailUrl = imagesLoaderRequestData?.siteThumbnailUrl
    if (siteThumbnailUrl != null) {
      val siteThumbnailImageRef = WeakReference(siteThumbnailImage)

      siteImageRequestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        siteThumbnailUrl.toString(),
        siteThumbnailImageSize,
        siteThumbnailImageSize,
        listOf(CIRCLE_CROP),
        { drawable ->
          siteThumbnailImageRef.get()?.visibility = View.VISIBLE
          siteThumbnailImageRef.get()?.setImageBitmap(drawable.bitmap)
        }
      )
    }
  }

  data class ImagesLoaderRequestData(
    val threadThumbnailUrl: HttpUrl,
    val siteThumbnailUrl: HttpUrl?
  )

  companion object {
    private val CIRCLE_CROP = CircleCropTransformation()
  }
}