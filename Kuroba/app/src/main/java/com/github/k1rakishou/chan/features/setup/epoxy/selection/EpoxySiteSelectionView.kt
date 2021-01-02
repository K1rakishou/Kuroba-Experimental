package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.doOnPreDraw
import coil.request.Disposable
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import java.lang.ref.WeakReference
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

  private val siteIcon: AppCompatImageView
  private val siteName: MaterialTextView

  private var requestDisposable: Disposable? = null

  init {
    inflate(context, R.layout.epoxy_site_selection_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    siteIcon = findViewById(R.id.site_icon)
    siteName = findViewById(R.id.site_name)
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
    updateSiteNameColor()
  }

  @ModelProp
  fun bindSiteName(name: String) {
    siteName.text = name
    updateSiteNameColor()
  }

  @ModelProp
  fun bindIcon(iconUrl: String) {
    val siteIconRef = WeakReference(siteIcon)

    siteIcon.doOnPreDraw {
      requestDisposable?.dispose()
      requestDisposable = null

      require(siteIcon.width > 0 && siteIcon.height > 0) { "View (siteIcon) has no size!" }

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        iconUrl,
        siteIcon.width,
        siteIcon.height,
        listOf(),
        { drawable -> siteIconRef.get()?.setImageBitmap(drawable.bitmap) },
        R.drawable.error_icon
      )
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

  private fun updateSiteNameColor() {
    siteName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

}