package com.github.k1rakishou.chan.features.setup.epoxy.site

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import coil.request.Disposable
import coil.transform.GrayscaleTransformation
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSwitchMaterial
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.android.material.textview.MaterialTextView
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

  private val siteIconView: AppCompatImageView
  private val siteName: MaterialTextView
  private val siteSwitch: ColorizableSwitchMaterial
  private val siteSettings: AppCompatImageView
  val siteReorder: AppCompatImageView

  var isArchiveSite: Boolean = false
    private set

  private var descriptor: SiteDescriptor? = null
  private var requestDisposable: Disposable? = null

  init {
    inflate(context, R.layout.epoxy_site_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    siteIconView = findViewById(R.id.site_icon)
    siteName = findViewById(R.id.site_name)
    siteSwitch = findViewById(R.id.site_switch)
    siteSettings = findViewById(R.id.site_settings)
    siteReorder = findViewById(R.id.site_reorder)

    siteSwitch.isClickable = false
    siteSwitch.isFocusable = false
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)

    siteSettings.setImageDrawable(
      themeEngine.getDrawableTinted(
        context,
        R.drawable.ic_settings_white_24dp,
        isDarkColor(themeEngine.chanTheme.backColor)
      )
    )

    siteReorder.setImageDrawable(
      themeEngine.getDrawableTinted(
        context,
        R.drawable.ic_reorder_white_24dp,
        isDarkColor(themeEngine.chanTheme.backColor)
      )
    )

    updateReorderTint()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    updateSiteNameTextColor()
    updateSettingsTint()
    updateReorderTint()
  }

  @ModelProp
  fun isArchiveSite(isArchive: Boolean) {
    this.isArchiveSite = isArchive

    if (this.isArchiveSite) {
      siteReorder.setVisibilityFast(View.GONE)
      siteIconView.updateMargins(left = ARCHIVE_SITE_ICON_LEFT_MARGIN)
    } else {
      siteReorder.setVisibilityFast(View.VISIBLE)
      siteIconView.updateMargins(left = 0)
    }
  }

  @ModelProp
  fun bindSiteName(name: String) {
    siteName.text = name
    updateSiteNameTextColor()
  }

  @ModelProp
  fun bindIcon(pair: Pair<SiteIcon, SiteEnableState>) {
    val siteIcon = pair.first
    val siteEnableState = pair.second

    val transformations = if (siteEnableState != SiteEnableState.Active) {
      listOf(GRAYSCALE)
    } else {
      listOf()
    }

    if (siteIcon.url != null) {
      requestDisposable?.dispose()
      requestDisposable = null

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context = context,
        url = siteIcon.url!!.toString(),
        cacheFileType = CacheFileType.SiteIcon,
        imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(siteIconView),
        transformations = transformations,
        listener = { drawable -> siteIconView.setImageBitmap(drawable.bitmap) },
        errorDrawableId = R.drawable.error_icon
      )
    } else if (siteIcon.drawable != null) {
      siteIconView.setImageBitmap(siteIcon.drawable!!.bitmap)
    }
  }

  @ModelProp(ModelProp.Option.NullOnRecycle)
  fun bindSwitch(siteEnableState: SiteEnableState?) {
    if (siteEnableState == null) {
      siteSwitch.isEnabled = true
      siteSwitch.isChecked = false

      siteSettings.alpha = themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat

      siteReorder.isEnabled = false
      siteReorder.alpha = themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat
      return
    }

    if (siteEnableState == SiteEnableState.Disabled) {
      siteSwitch.isChecked = false
      siteSwitch.isEnabled = false

      siteSettings.isEnabled = false
      siteSettings.alpha = themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat

      siteReorder.isEnabled = false
      siteReorder.alpha = themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat
      return
    }

    siteSwitch.isEnabled = true
    siteSwitch.isChecked = siteEnableState == SiteEnableState.Active

    siteSettings.isEnabled = siteEnableState == SiteEnableState.Active
    siteReorder.isEnabled = siteEnableState == SiteEnableState.Active

    val alpha = if (siteEnableState == SiteEnableState.Active) {
      1f
    } else {
      themeEngine.chanTheme.defaultColors.disabledControlAlphaFloat
    }

    siteSettings.alpha = alpha
    siteReorder.alpha = alpha

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

    siteIconView.setImageBitmap(null)
  }

  private fun updateReorderTint() {
    if (siteReorder.drawable == null) {
      return
    }

    val isDarkColor = isDarkColor(themeEngine.chanTheme.backColor)
    siteReorder.setImageDrawable(themeEngine.tintDrawable(siteReorder.drawable, isDarkColor))
  }

  private fun updateSettingsTint() {
    if (siteSettings.drawable == null) {
      return
    }

    val isDarkColor = isDarkColor(themeEngine.chanTheme.backColor)
    siteSettings.setImageDrawable(themeEngine.tintDrawable(siteSettings.drawable, isDarkColor))
  }

  private fun updateSiteNameTextColor() {
    siteName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

  companion object {
    private val GRAYSCALE = GrayscaleTransformation()
    private val ARCHIVE_SITE_ICON_LEFT_MARGIN = dp(32f)
  }
}