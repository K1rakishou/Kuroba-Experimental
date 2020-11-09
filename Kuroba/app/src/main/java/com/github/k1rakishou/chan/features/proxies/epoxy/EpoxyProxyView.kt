package com.github.k1rakishou.chan.features.proxies.epoxy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.proxies.ProxySelectionHelper
import com.github.k1rakishou.chan.features.proxies.data.ProxyEntryViewSelection
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSwitchMaterial
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.view.SelectionCheckView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyProxyView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private var proxySelectionHelper: ProxySelectionHelper? = null

  private val proxyEntryViewHolder: LinearLayout
  private val proxyAddress: ColorizableTextView
  private val proxyPort: ColorizableTextView
  private val proxyEnableSwitch: ColorizableSwitchMaterial
  private val proxySettings: AppCompatImageView
  private val proxyType: MaterialTextView
  private val proxySupportedSites: MaterialTextView
  private val proxySupportedActions: MaterialTextView
  private val proxySelectionCheckView: SelectionCheckView

  init {
    inflate(context, R.layout.epoxy_proxy_entry_view, this)

    AppModuleAndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    proxyEntryViewHolder = findViewById(R.id.proxy_entry_view_holder)
    proxyAddress = findViewById(R.id.proxy_address)
    proxyPort = findViewById(R.id.proxy_port)
    proxyEnableSwitch = findViewById(R.id.proxy_enable_switch)
    proxySettings = findViewById(R.id.proxy_settings)
    proxyType = findViewById(R.id.proxy_type)
    proxySupportedSites = findViewById(R.id.proxy_supported_sites)
    proxySupportedActions = findViewById(R.id.proxy_supported_actions)
    proxySelectionCheckView = findViewById(R.id.proxy_selection_check_view)

    onThemeChanged()
  }

  override fun onThemeChanged() {
    updateProxyTypeTextColor()
    updateProxySupportedSitesTextColor()
    updateProxySupportedActionsTextColor()
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.DoNotHash])
  fun setProxySelectionHelper(proxySelectionHelper: ProxySelectionHelper?) {
    this.proxySelectionHelper = proxySelectionHelper
  }

  @ModelProp
  fun proxyAddress(address: String) {
    proxyAddress.text = context.getString(R.string.epoxy_proxy_view_address, address)
  }

  @ModelProp
  fun proxyPort(port: String) {
    proxyPort.text = context.getString(R.string.epoxy_proxy_view_port, port)
  }

  @ModelProp
  fun proxyEnabled(enabled: Boolean) {
    proxyEnableSwitch.isChecked = enabled
  }

  @ModelProp
  fun proxySupportedSites(sites: String) {
    proxySupportedSites.text = context.getString(R.string.epoxy_proxy_view_supported_site, sites)
    updateProxySupportedSitesTextColor()
  }

  @ModelProp
  fun proxySupportedActions(actions: String) {
    proxySupportedActions.text = context.getString(R.string.epoxy_proxy_view_supported_actions, actions)
    updateProxySupportedActionsTextColor()
  }

  @ModelProp
  fun proxyType(type: String) {
    proxyType.text = context.getString(R.string.epoxy_proxy_view_type, type)
    updateProxyTypeTextColor()
  }

  @ModelProp
  fun proxySelection(proxyEntryViewSelection: ProxyEntryViewSelection?) {
    if (proxyEntryViewSelection == null) {
      proxySelectionCheckView.setVisibilityFast(View.GONE)
      proxyEntryViewHolder.setBackgroundColorFast(Color.TRANSPARENT)
      return
    }

    val isSelected = proxyEntryViewSelection.selected
    proxySelectionCheckView.setVisibilityFast(View.VISIBLE)
    proxySelectionCheckView.setChecked(isSelected)

    if (isSelected) {
      proxySettings.isFocusable = false
      proxySettings.isFocusable = false

      val accent = ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
        .withAlpha(HIGHLIGHT_COLOR_ALPHA)

      proxyEntryViewHolder.setBackgroundColorFast(accent.defaultColor)
    } else {
      proxySettings.isFocusable = true
      proxySettings.isFocusable = true

      proxyEntryViewHolder.setBackgroundColorFast(Color.TRANSPARENT)
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun proxyHolderClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      proxyEntryViewHolder.setOnClickListener(null)
      return
    }

    proxyEntryViewHolder.setOnClickListener {
      val isInSelectionMode = proxySelectionHelper?.isInSelectionMode()
        ?: return@setOnClickListener

      if (!isInSelectionMode) {
        proxyEnableSwitch.isChecked = !proxyEnableSwitch.isChecked
      }

      listener.invoke()
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun proxyHolderLongClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      proxyEntryViewHolder.setOnLongClickListener(null)
      return
    }

    proxyEntryViewHolder.setOnLongClickListener {
      listener.invoke()
      return@setOnLongClickListener true
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun proxySettingsClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      proxySettings.setOnClickListener(null)
      return
    }

    proxySettings.setOnClickListener {
      listener.invoke()
    }
  }

  private fun updateProxySupportedActionsTextColor() {
    proxySupportedActions.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

  private fun updateProxySupportedSitesTextColor() {
    proxySupportedSites.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

  private fun updateProxyTypeTextColor() {
    proxyType.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

  companion object {
    private const val HIGHLIGHT_COLOR_ALPHA = 50
  }
}