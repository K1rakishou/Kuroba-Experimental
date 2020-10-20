package com.github.k1rakishou.chan.features.proxies

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.features.proxies.data.ProxySetupState
import com.github.k1rakishou.chan.features.proxies.epoxy.epoxyProxyView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ProxySetupController(context: Context) : Controller(context), ProxySetupView {
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var addProxyButton: ColorizableFloatingActionButton

  private val presenter = ProxySetupPresenter()

  private val onApplyClickListener = { presenter.reloadProxies() }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_proxy_setup_title)

    view = AndroidUtils.inflate(context, R.layout.controller_proxy_setup)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    addProxyButton = view.findViewById(R.id.add_proxy_button)

    addProxyButton.setOnClickListener {
      requireNavController().pushController(ProxyEditorController(context, onApplyClickListener))
    }

    mainScope.launch {
      presenter.listenForStateUpdates()
        .collect { state -> onStateChanged(state) }
    }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    presenter.onDestroy()
  }

  override fun showMessage(message: String) {
    showToast(message)
  }

  private fun onStateChanged(state: ProxySetupState) {
    epoxyRecyclerView.withModels {
      when (state) {
        ProxySetupState.Uninitialized -> {
          // no-op
        }
        ProxySetupState.Empty -> {
          epoxyTextView {
            id("no_proxies_text_view")
            message(context.getString(R.string.controller_proxy_setup_no_proxies))
          }
        }
        is ProxySetupState.Data -> {
          state.proxyEntryViewList.forEach { proxyEntryView ->
            epoxyProxyView {
              id("epoxy_proxy_view_${proxyEntryView.proxyKeyString()}")
              proxyAddress(proxyEntryView.address)
              proxyPort(proxyEntryView.port.toString())
              proxyEnabled(proxyEntryView.enabled)
              proxySupportedSites(proxyEntryView.supportedSites)
              proxySupportedActions(proxyEntryView.supportedActions)
              proxyType(proxyEntryView.proxyType)
              proxyHolderClickListener {
                presenter.toggleProxyEnableDisableState(proxyEntryView)
              }
              proxySettingsClickListener {
                val controller = ProxyEditorController(
                  context,
                  onApplyClickListener,
                  proxyEntryView.proxyKey()
                )

                requireNavController().pushController(controller)
              }
            }
          }
        }
      }
    }
  }

}