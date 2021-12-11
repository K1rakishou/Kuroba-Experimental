package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import coil.request.Disposable
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySiteSelectionView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val siteIconView: AppCompatImageView
  private val siteName: MaterialTextView
  private val divider: View

  private var requestDisposable: Disposable? = null

  init {
    inflate(context, R.layout.epoxy_site_selection_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    siteIconView = findViewById(R.id.site_icon)
    siteName = findViewById(R.id.site_name)
    divider = findViewById(R.id.divider)

    onThemeChanged()
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
    siteName.setTextColor(themeEngine.chanTheme.textColorPrimary)

    val dividerColor = themeEngine.resolveDrawableTintColor(themeEngine.chanTheme.isBackColorDark)
    divider.setBackgroundColor(dividerColor)
  }

  @ModelProp
  fun bindSiteName(name: String) {
    siteName.text = name
    onThemeChanged()
  }

  @ModelProp
  fun bindIcon(siteIcon: SiteIcon) {
    requestDisposable?.dispose()
    requestDisposable = null

    if (siteIcon.url != null) {
      requestDisposable = imageLoaderV2.loadFromNetwork(
        context = context,
        url = siteIcon.url!!.toString(),
        cacheFileType = CacheFileType.SiteIcon,
        imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(siteIconView),
        transformations = listOf(),
        listener = { drawable -> siteIconView.setImageBitmap(drawable.bitmap) },
        errorDrawableId = R.drawable.error_icon
      )
    } else if (siteIcon.drawable != null) {
      siteIconView.setImageBitmap(siteIcon.drawable!!.bitmap)
    }
  }

  @CallbackProp
  fun bindRowClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      setOnClickListener(null)
      return
    }

    setOnClickListener {
      callback.invoke()
    }
  }

  @OnViewRecycled
  fun unbind() {
    this.requestDisposable?.dispose()
    this.requestDisposable = null
  }

}