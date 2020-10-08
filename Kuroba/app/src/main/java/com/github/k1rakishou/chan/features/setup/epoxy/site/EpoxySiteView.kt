package com.github.k1rakishou.chan.features.setup.epoxy.site

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.doOnPreDraw
import coil.request.Disposable
import coil.transform.GrayscaleTransformation
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSwitchMaterial
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.android.material.textview.MaterialTextView
import java.lang.ref.WeakReference
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySiteView @JvmOverloads constructor(
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
  private val siteSwitch: ColorizableSwitchMaterial
  private val siteSettings: AppCompatImageView

  private var descriptor: SiteDescriptor? = null
  private var requestDisposable: Disposable? = null

  init {
    inflate(context, R.layout.epoxy_site_view, this)
    Chan.inject(this)

    siteIcon = findViewById(R.id.site_icon)
    siteName = findViewById(R.id.site_name)
    siteSwitch = findViewById(R.id.site_switch)
    siteSettings = findViewById(R.id.site_settings)

    siteSwitch.isClickable = false
    siteSwitch.isFocusable = false

    siteIcon.setImageBitmap(null)

    val tintedDrawable = themeEngine.getDrawableTinted(
      context,
      R.drawable.ic_settings_white_24dp,
      AndroidUtils.isDarkColor(themeEngine.chanTheme.backColor)
    )

    siteSettings.setImageDrawable(tintedDrawable)
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
    updateSiteNameTextColor()
    updateSettingsTint()
  }

  @ModelProp
  fun bindSiteName(name: String) {
    siteName.text = name
    updateSiteNameTextColor()
  }

  @ModelProp
  fun bindIcon(pair: Pair<String, SiteEnableState>) {
    val siteIconRef = WeakReference(siteIcon)
    val iconUrl = pair.first
    val siteEnableState = pair.second

    val transformations = if (siteEnableState != SiteEnableState.Active) {
      listOf(GRAYSCALE)
    } else {
      listOf()
    }

    siteIcon.doOnPreDraw {
      requestDisposable?.dispose()
      requestDisposable = null

      require(siteIcon.width > 0 && siteIcon.height > 0) { "View (siteIcon) has no size!" }

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        iconUrl,
        siteIcon.width,
        siteIcon.height,
        transformations,
        { drawable -> siteIconRef.get()?.setImageBitmap(drawable.bitmap) },
        R.drawable.error_icon
      )
    }
  }

  @ModelProp(ModelProp.Option.NullOnRecycle)
  fun bindSwitch(siteEnableState: SiteEnableState?) {
    if (siteEnableState == null) {
      siteSwitch.isEnabled = true
      siteSwitch.isChecked = false
      siteSettings.alpha = themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat
      return
    }

    if (siteEnableState == SiteEnableState.Disabled) {
      siteSwitch.isChecked = false
      siteSwitch.isEnabled = false

      siteSettings.isEnabled = false
      siteSettings.alpha = themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat
      return
    }

    siteSwitch.isEnabled = true
    siteSwitch.isChecked = siteEnableState == SiteEnableState.Active

    siteSettings.isEnabled = siteEnableState == SiteEnableState.Active
    siteSettings.alpha = if (siteEnableState == SiteEnableState.Active) {
      1f
    } else {
      themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat
    }

    updateSettingsTint()
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.DoNotHash])
  fun bindRowClickCallback(pair: Pair<((enabled: Boolean) -> Unit), SiteEnableState>?) {
    val callback = pair?.first
    val siteEnableState = pair?.second

    if (callback == null && siteEnableState == null) {
      setOnClickListener(null)
      return
    }

    if (siteEnableState == SiteEnableState.Disabled) {
      setOnClickListener { callback?.invoke(false) }
      return
    }

    setOnClickListener {
      if (callback == null) {
        return@setOnClickListener
      }

      siteSwitch.isChecked = !siteSwitch.isChecked
      callback.invoke(siteSwitch.isChecked)
    }
  }

  @CallbackProp
  fun bindSettingClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      siteSettings.setOnClickListener(null)
      return
    }

    siteSettings.setOnClickListener { callback.invoke() }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.DoNotHash])
  fun setSiteDescriptor(siteDescriptor: SiteDescriptor?) {
    this.descriptor = siteDescriptor
  }

  @OnViewRecycled
  fun unbind() {
    this.requestDisposable?.dispose()
    this.requestDisposable = null
  }

  private fun updateSettingsTint() {
    if (siteSettings.drawable == null) {
      return
    }

    val isDarkColor = AndroidUtils.isDarkColor(themeEngine.chanTheme.backColor)
    siteSettings.setImageDrawable(themeEngine.tintDrawable(siteSettings.drawable, isDarkColor))
  }

  private fun updateSiteNameTextColor() {
    siteName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

  companion object {
    private val GRAYSCALE = GrayscaleTransformation()
  }
}