package com.github.k1rakishou.chan.features.proxies

import android.content.Context
import android.widget.LinearLayout
import android.widget.RadioGroup
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteRegistry
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableChip
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextInputLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.findChildren
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject


class ProxyEditorController(
  context: Context,
  private val applyClickListener: () -> Unit,
  private val proxyKey: ProxyStorage.ProxyKey? = null
) : Controller(context) {

  @Inject
  lateinit var proxyStorage: ProxyStorage
  @Inject
  lateinit var siteManager: SiteManager

  private val siteRegistry = SiteRegistry

  private lateinit var proxyAddressTIL: ColorizableTextInputLayout
  private lateinit var proxyAddress: ColorizableEditText
  private lateinit var proxyPortTIL: ColorizableTextInputLayout
  private lateinit var proxyPort: ColorizableEditText
  private lateinit var proxyType: RadioGroup
  private lateinit var proxySitesChipGroup: ChipGroup
  private lateinit var proxySave: ColorizableButton
  private lateinit var enableForSiteRequests: ColorizableCheckBox
  private lateinit var enableForMediaPreviews: ColorizableCheckBox
  private lateinit var enableForFullMedia: ColorizableCheckBox

  private var isControllerVisible = true
  private val saveProxyExecutor = RendezvousCoroutineExecutor(mainScope)

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_proxy_editor_title)
    view = inflate(context, R.layout.controller_proxy_editor)

    proxyAddressTIL = view.findViewById(R.id.proxy_address_text_input_layout)
    proxyAddress = view.findViewById(R.id.proxy_address)
    proxyPortTIL = view.findViewById(R.id.proxy_port_text_input_layout)
    proxyPort = view.findViewById(R.id.proxy_port)
    proxyType = view.findViewById(R.id.proxy_type)
    proxySitesChipGroup = view.findViewById(R.id.proxy_sites_group)
    proxySave = view.findViewById(R.id.proxy_save)
    enableForSiteRequests = view.findViewById(R.id.enable_for_site_requests)
    enableForMediaPreviews = view.findViewById(R.id.enable_for_media_previews)
    enableForFullMedia = view.findViewById(R.id.enable_for_full_media)

    siteRegistry.SITE_CLASSES_MAP.keys.forEach { siteDescriptor ->
      if (siteManager.bySiteDescriptor(siteDescriptor)?.isSynthetic == true) {
        return@forEach
      }

      val siteChip = ColorizableChip(context)
      val chipDrawable = ChipDrawable.createFromAttributes(
        context,
        null,
        0,
        R.style.Widget_MaterialComponents_Chip_Choice
      )
      siteChip.setChipDrawable(chipDrawable)

      siteChip.layoutParams = ChipGroup.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

      siteChip.updatePaddings(dp(8f), 0, 0, dp(8f))
      siteChip.tag = siteDescriptor
      siteChip.text = siteDescriptor.siteName

      proxySitesChipGroup.addView(siteChip)
    }

    proxyKey?.let { key ->
      val kurobaProxy = proxyStorage.getProxyByProxyKey(key)
      if (kurobaProxy != null) {
        fillInProxyValues(kurobaProxy)
      }
    }

    proxySave.setOnClickListener {
      saveProxyExecutor.post {
        enableDisableUi(enable = false)

        val success = try {
          saveProxy()
        } finally {
          enableDisableUi(enable = true)
        }

        if (!success) {
          return@post
        }

        if (proxyKey == null) {
          showToast(R.string.controller_proxy_editor_proxy_added)
        } else {
          showToast(R.string.controller_proxy_editor_proxy_updated)
        }

        applyClickListener.invoke()
        pop()
      }
    }
  }

  private fun fillInProxyValues(kurobaProxy: ProxyStorage.KurobaProxy) {
    proxyAddress.setText(kurobaProxy.address)
    proxyPort.setText(kurobaProxy.port.toString())

    when (kurobaProxy.proxyType) {
      ProxyStorage.KurobaProxyType.HTTP -> proxyType.check(R.id.proxy_type_http)
      ProxyStorage.KurobaProxyType.SOCKS -> proxyType.check(R.id.proxy_type_socks)
    }

    proxySitesChipGroup
      .findChildren<ColorizableChip> { view -> view is ColorizableChip && view.tag is SiteDescriptor }
      .filter { colorizableChip -> (colorizableChip.tag as SiteDescriptor) in kurobaProxy.supportedSites }
      .forEach { colorizableChip -> colorizableChip.isChecked = true }

    kurobaProxy.supportedActions.forEach { proxyActionType ->
      when (proxyActionType) {
        ProxyStorage.ProxyActionType.SiteRequests -> enableForSiteRequests.isChecked = true
        ProxyStorage.ProxyActionType.SiteMediaPreviews -> enableForMediaPreviews.isChecked = true
        ProxyStorage.ProxyActionType.SiteMediaFull -> enableForFullMedia.isChecked = true
      }
    }
  }

  private fun pop() {
    if (isControllerVisible) {
      isControllerVisible = false
      requireNavController().popController()
    }
  }

  private suspend fun saveProxy(): Boolean {
    proxyAddressTIL.error = null
    val proxyAddress = proxyAddress.text?.toString()

    if (proxyAddress == null || !isValidAddress(proxyAddress)) {
      proxyAddressTIL.error = getString(R.string.controller_proxy_editor_proxy_address_is_not_valid)
      showToast(R.string.controller_proxy_editor_proxy_add_error)
      return false
    }

    val inetAddressResult = validateProxyAddress(proxyAddress)
    if (inetAddressResult.isFailure) {
      proxyAddressTIL.error = inetAddressResult.exceptionOrNull()!!.errorMessageOrClassName()
      showToast(R.string.controller_proxy_editor_proxy_add_error)
      return false
    }

    val proxyPort = proxyPort.text?.toString()?.toIntOrNull()
    if (proxyPort == null || !isValidPort(proxyPort)) {
      proxyPortTIL.error = getString(R.string.controller_proxy_editor_proxy_port_is_not_valid)
      showToast(R.string.controller_proxy_editor_proxy_add_error)
      return false
    }

    val newProxyKey = ProxyStorage.ProxyKey(proxyAddress, proxyPort)

    val proxyType = when (proxyType.checkedRadioButtonId) {
      R.id.proxy_type_http -> ProxyStorage.KurobaProxyType.HTTP
      R.id.proxy_type_socks -> ProxyStorage.KurobaProxyType.SOCKS
      else -> null
    }

    if (proxyType == null) {
      showToast(R.string.controller_proxy_editor_proxy_type_is_not_valid)
      return false
    }

    val selectedSites = proxySitesChipGroup
      .findChildren<ColorizableChip> { view -> view is ColorizableChip && view.tag is SiteDescriptor }
      .filter { siteChip -> siteChip.isChecked }
      .map { siteChip -> siteChip.tag as SiteDescriptor }
      .toSet()

    if (selectedSites.isEmpty()) {
      showToast(R.string.controller_proxy_editor_proxy_no_sites_selected)
      return false
    }

    val supportedSelectors = mutableSetOf<ProxyStorage.ProxyActionType>().apply {
      if (enableForSiteRequests.isChecked) {
        add(ProxyStorage.ProxyActionType.SiteRequests)
      }
      if (enableForMediaPreviews.isChecked) {
        add(ProxyStorage.ProxyActionType.SiteMediaPreviews)
      }
      if (enableForFullMedia.isChecked) {
        add(ProxyStorage.ProxyActionType.SiteMediaFull)
      }
    }

    if (supportedSelectors.isEmpty()) {
      showToast(R.string.controller_proxy_editor_proxy_no_actions_selected)
      return false
    }

    if (proxyKey != null && proxyKey != newProxyKey) {
      val success = proxyStorage.deleteProxies(listOf(proxyKey))
        .safeUnwrap { error ->
          Logger.e(TAG, "proxyStorage.deleteProxies($proxyKey) error", error)
          showToast("Failed to delete old proxy, error=${error.errorMessageOrClassName()}")
          return false
        }

      if (!success) {
        showToast("Failed to delete old proxy")
        return false
      }
    }

    val order = proxyStorage.getNewProxyOrder()

    val newProxy = ProxyStorage.KurobaProxy(
      address = proxyAddress,
      port = proxyPort,
      enabled = true,
      order = order,
      supportedSites = selectedSites,
      supportedActions = supportedSelectors,
      proxyType = proxyType
    )

    val addNewProxyResult = proxyStorage.addNewProxy(newProxy)
    if (addNewProxyResult is ModularResult.Value && addNewProxyResult.value) {
      return true
    }

    if (addNewProxyResult is ModularResult.Error) {
      Logger.e(TAG, "addNewProxy error", addNewProxyResult.error)

      showToast(getString(
          R.string.controller_proxy_editor_failed_to_persist_new_proxy_error,
          addNewProxyResult.error.errorMessageOrClassName()
        ))
    } else {
      showToast(R.string.controller_proxy_editor_failed_to_persist_new_proxy_unknown_error)
    }

    return false
  }

  private suspend fun validateProxyAddress(proxyAddress: String): Result<InetAddress> {
    return Result.runCatching {
      return@runCatching withContext(Dispatchers.IO) { InetAddress.getByName(proxyAddress) }
    }
  }

  private fun isValidPort(port: Int): Boolean {
    return port in 0..65535
  }

  private fun isValidAddress(address: String?): Boolean {
    if (address.isNullOrBlank()) {
      return false
    }

    return true
  }

  private fun enableDisableUi(enable: Boolean) {
    proxyAddressTIL.isEnabled = enable
    proxyAddress.isEnabled = enable
    proxyPortTIL.isEnabled = enable
    proxyPort.isEnabled = enable
    proxyType.isEnabled = enable
    proxySitesChipGroup.isEnabled = enable
    proxySave.isEnabled = enable
    enableForSiteRequests.isEnabled = enable
    enableForMediaPreviews.isEnabled = enable
    enableForFullMedia.isEnabled = enable
  }

  companion object {
    private const val TAG = "ProxyEditorController"
  }

}