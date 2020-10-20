package com.github.k1rakishou.chan.features.proxies.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSwitchMaterial
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
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

  private val proxyEntryViewHolder: LinearLayout
  private val proxyAddress: ColorizableTextView
  private val proxyPort: ColorizableTextView
  private val proxyEnableSwitch: ColorizableSwitchMaterial
  private val proxySettings: AppCompatImageView
  private val proxyType: MaterialTextView
  private val proxySupportedSites: MaterialTextView
  private val proxySupportedActions: MaterialTextView

  init {
    Chan.inject(this)
    inflate(context, R.layout.epoxy_proxy_entry_view, this)

    proxyEntryViewHolder = findViewById(R.id.proxy_entry_view_holder)
    proxyAddress = findViewById(R.id.proxy_address)
    proxyPort = findViewById(R.id.proxy_port)
    proxyEnableSwitch = findViewById(R.id.proxy_enable_switch)
    proxySettings = findViewById(R.id.proxy_settings)
    proxyType = findViewById(R.id.proxy_type)
    proxySupportedSites = findViewById(R.id.proxy_supported_sites)
    proxySupportedActions = findViewById(R.id.proxy_supported_actions)

    onThemeChanged()
  }

  override fun onThemeChanged() {
    updateProxyTypeTextColor()
    updateProxySupportedSitesTextColor()
    updateProxySupportedActionsTextColor()
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

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun proxyHolderClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      proxyEntryViewHolder.setOnClickListener(null)
      return
    }

    proxyEntryViewHolder.setOnClickListener {
      proxyEnableSwitch.isChecked = !proxyEnableSwitch.isChecked
      listener.invoke()
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

}