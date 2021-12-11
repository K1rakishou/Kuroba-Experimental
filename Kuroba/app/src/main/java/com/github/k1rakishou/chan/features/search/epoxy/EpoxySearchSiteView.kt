package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.doOnPreDraw
import coil.request.Disposable
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import java.lang.ref.WeakReference
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
internal class EpoxySearchSiteView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val siteIcon: AppCompatImageView
  private val siteName: MaterialTextView

  private var requestDisposable: Disposable? = null

  init {
    inflate(context, R.layout.epoxy_search_site_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    siteIcon = findViewById(R.id.site_icon)
    siteName = findViewById(R.id.site_name)
  }

  @OnViewRecycled
  fun unbind() {
    this.requestDisposable?.dispose()
    this.requestDisposable = null
  }

  @ModelProp
  fun itemBackgroundColor(color: Int?) {
    if (color == null) {
      return
    }

    setBackgroundColorFast(color)
  }

  @ModelProp
  fun bindSiteName(name: String) {
    siteName.text = name
    siteName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

  @ModelProp
  fun bindIconUrl(iconUrl: String?) {
    if (iconUrl == null) {
      this.requestDisposable?.dispose()
      this.requestDisposable = null
      this.siteIcon.setImageBitmap(null)

      return
    }

    val siteIconRef = WeakReference(siteIcon)

    siteIcon.doOnPreDraw {
      requestDisposable?.dispose()
      requestDisposable = null

      require(siteIcon.width > 0 && siteIcon.height > 0) { "View (siteIcon) has no size!" }

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context = context,
        url = iconUrl.toString(),
        cacheFileType = CacheFileType.SiteIcon,
        imageSize = ImageLoaderV2.ImageSize.FixedImageSize(
          width = SiteIcon.FAVICON_SIZE,
          height = SiteIcon.FAVICON_SIZE,
        ),
        transformations = emptyList(),
        listener = { drawable -> siteIconRef.get()?.setImageBitmap(drawable.bitmap) },
        errorDrawableId = R.drawable.error_icon,
        notFoundDrawableId = R.drawable.error_icon
      )
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun bindClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      setOnThrottlingClickListener(null)
      return
    }

    setOnThrottlingClickListener { callback.invoke() }
  }

}