package com.github.k1rakishou.chan.features.drawer.epoxy

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import coil.request.Disposable
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import com.airbnb.epoxy.AfterPropsSet
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.drawer.data.ImagesLoaderRequestData
import com.github.k1rakishou.chan.features.drawer.data.NavHistoryBookmarkAdditionalInfo
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT, fullSpan = false)
class EpoxyHistoryGridEntryView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine

  private var imagesLoaderRequestData: ImagesLoaderRequestData? = null
  private var threadImageRequestDisposable: Disposable? = null
  private var siteImageRequestDisposable: Disposable? = null
  private var descriptor: ChanDescriptor? = null
  private var additionalInfo: NavHistoryBookmarkAdditionalInfo? = null

  private val viewHolder: ConstraintLayout
  private val threadThumbnailImage: AppCompatImageView
  private val siteThumbnailImage: AppCompatImageView
  private val title: AppCompatTextView
  private val historyEntryInfo: AppCompatTextView
  private val threadThumbnailImageSize: Int
  private val siteThumbnailImageSize: Int

  init {
    inflate(context, R.layout.epoxy_history_grid_entry_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    viewHolder = findViewById(R.id.history_entry_view_holder)
    threadThumbnailImage = findViewById(R.id.history_entry_thread_image)
    siteThumbnailImage = findViewById(R.id.history_entry_site_image)
    title = findViewById(R.id.history_entry_title)
    historyEntryInfo = findViewById(R.id.history_entry_info)

    threadThumbnailImageSize = context.resources.getDimension(R.dimen.history_entry_thread_image_size).toInt()
    siteThumbnailImageSize = context.resources.getDimension(R.dimen.history_entry_site_image_size).toInt()

    threadThumbnailImage.visibility = View.VISIBLE
    siteThumbnailImage.visibility = View.VISIBLE
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
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

    threadThumbnailImage.updateLayoutParams<ViewGroup.LayoutParams> {
      width = ViewGroup.LayoutParams.MATCH_PARENT
    }
  }

  override fun onThemeChanged() {
    title.setTextColor(themeEngine.chanTheme.textColorPrimary)
    bindNavHistoryBookmarkAdditionalInfo(additionalInfo)
  }

  @ModelProp(ModelProp.Option.DoNotHash)
  fun setImageLoaderRequestData(imagesLoaderRequestData: ImagesLoaderRequestData?) {
    this.imagesLoaderRequestData = imagesLoaderRequestData
  }

  @ModelProp(options = [ModelProp.Option.DoNotHash])
  fun setDescriptor(descriptor: ChanDescriptor) {
    this.descriptor = descriptor
  }

  @ModelProp
  fun setTitle(titleText: String) {
    title.text = titleText
    title.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

  @ModelProp
  fun bindNavHistoryBookmarkAdditionalInfo(additionalInfo: NavHistoryBookmarkAdditionalInfo?) {
    this.additionalInfo = additionalInfo

    if (additionalInfo == null) {
      historyEntryInfo.visibility = View.GONE
      return
    }

    historyEntryInfo.visibility = View.VISIBLE
    historyEntryInfo.text = additionalInfo.newPosts.toString()

    if (additionalInfo.newQuotes > 0) {
      historyEntryInfo.setTextColor(themeEngine.chanTheme.bookmarkCounterHasRepliesColor)
    } else if (!additionalInfo.watching) {
      historyEntryInfo.setTextColor(themeEngine.chanTheme.bookmarkCounterNotWatchingColor)
    } else {
      historyEntryInfo.setTextColor(themeEngine.chanTheme.bookmarkCounterNormalColor)
    }

    historyEntryInfo.setTypeface(historyEntryInfo.typeface, Typeface.NORMAL)
    historyEntryInfo.paintFlags = Paint.ANTI_ALIAS_FLAG

    if (additionalInfo.isBumpLimit && additionalInfo.isImageLimit) {
      historyEntryInfo.setTypeface(historyEntryInfo.typeface, Typeface.BOLD_ITALIC)
    } else if (additionalInfo.isBumpLimit) {
      historyEntryInfo.setTypeface(historyEntryInfo.typeface, Typeface.ITALIC)
    } else if (additionalInfo.isImageLimit) {
      historyEntryInfo.setTypeface(historyEntryInfo.typeface, Typeface.BOLD)
    }

    if (additionalInfo.isLastPage) {
      historyEntryInfo.paintFlags = historyEntryInfo.paintFlags or Paint.UNDERLINE_TEXT_FLAG
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

  @AfterPropsSet
  fun afterPropsSet() {
    threadThumbnailImage.updatePadding(
      left = THREAD_THUMBNAIL_PADDING,
      right = THREAD_THUMBNAIL_PADDING,
      top = THREAD_THUMBNAIL_PADDING,
      bottom = THREAD_THUMBNAIL_PADDING
    )

    if (imagesLoaderRequestData == null || descriptor == null) {
      threadThumbnailImage.setImageBitmap(null)
      siteThumbnailImage.setImageBitmap(null)
      return
    }

    val threadThumbnailUrl = imagesLoaderRequestData?.threadThumbnailUrl
    if (threadThumbnailUrl != null) {
      val transformation = if (descriptor is ChanDescriptor.ICatalogDescriptor) {
        threadThumbnailImage.updateLayoutParams<ViewGroup.LayoutParams> {
          width = context.resources.getDimension(R.dimen.history_entry_thread_image_size).toInt()
        }

        CIRCLE_CROP
      } else {
        threadThumbnailImage.updateLayoutParams<ViewGroup.LayoutParams> {
          width = ViewGroup.LayoutParams.MATCH_PARENT
        }

        ROUNDED_CORNERS
      }

      threadImageRequestDisposable = imageLoaderV2.loadFromNetwork(
        context = context,
        url = threadThumbnailUrl.toString(),
        cacheFileType = CacheFileType.BookmarkThumbnail,
        imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(threadThumbnailImage),
        transformations = listOf(transformation),
        listener = { drawable -> threadThumbnailImage.setImageBitmap(drawable.bitmap) }
      )
    }

    val siteThumbnailUrl = imagesLoaderRequestData?.siteThumbnailUrl
    if (siteThumbnailUrl != null) {
      siteImageRequestDisposable = imageLoaderV2.loadFromNetwork(
        context = context,
        url = siteThumbnailUrl.toString(),
        cacheFileType = CacheFileType.BookmarkThumbnail,
        imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(siteThumbnailImage),
        transformations = listOf(CIRCLE_CROP),
        listener = { drawable -> siteThumbnailImage.setImageBitmap(drawable.bitmap) }
      )
    }
  }

  companion object {
    private val CIRCLE_CROP = CircleCropTransformation()
    private val ROUNDED_CORNERS = RoundedCornersTransformation(
      AppModuleAndroidUtils.dp(1f).toFloat(),
      AppModuleAndroidUtils.dp(1f).toFloat(),
      AppModuleAndroidUtils.dp(1f).toFloat(),
      AppModuleAndroidUtils.dp(1f).toFloat()
    )

    private val THREAD_THUMBNAIL_PADDING = AppModuleAndroidUtils.dp(4f)
  }

}
