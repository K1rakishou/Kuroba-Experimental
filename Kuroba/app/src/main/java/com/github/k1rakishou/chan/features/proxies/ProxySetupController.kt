package com.github.k1rakishou.chan.features.proxies

import android.content.Context
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.proxies.data.ProxyEntryView
import com.github.k1rakishou.chan.features.proxies.data.ProxySetupState
import com.github.k1rakishou.chan.features.proxies.epoxy.epoxyProxyView
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.epoxy.epoxyDividerView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.ui.view.insets.ColorizableInsetAwareEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class ProxySetupController(
  context: Context
) : Controller(context), ProxySetupView, WindowInsetsListener, ProxySelectionHelper.OnProxyItemClicked {

  @Inject
  lateinit var proxyStorage: ProxyStorage
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private lateinit var epoxyRecyclerView: ColorizableInsetAwareEpoxyRecyclerView
  private lateinit var addProxyButton: ColorizableFloatingActionButton

  private val proxySelectionHelper = ProxySelectionHelper(this)

  private val presenter by lazy {
    ProxySetupPresenter(
      proxySelectionHelper = proxySelectionHelper,
      proxyStorage = proxyStorage
    )
  }

  private val onApplyClickListener = { presenter.reloadProxies() }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.controller_proxy_setup_title)
      )
    )

    view = inflate(context, R.layout.controller_proxy_setup)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    addProxyButton = view.findViewById(R.id.add_proxy_button)

    addProxyButton.setOnClickListener {
      requireNavController().pushController(ProxyEditorController(context, onApplyClickListener))
    }

    controllerScope.launch {
      presenter.proxySetupState
        .collect { state -> onStateChanged(state) }
    }

    controllerScope.launch {
      proxySelectionHelper.listenForSelectionChanges()
        .collect { selectionEvent -> onNewSelectionEvent(selectionEvent) }
    }

    controllerScope.launch {
      globalUiStateHolder.bottomPanel.bottomPanelHeight
        .onEach { onInsetsChanged() }
        .collect()
    }

    onInsetsChanged()
    presenter.onCreate(this)
    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    showProxyEditingNotification()
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    epoxyRecyclerView.clear()
    presenter.onDestroy()
    requireBottomPanelContract().hideBottomPanel(controllerKey)
  }

  override fun onBack(): Boolean {
    val result = requireBottomPanelContract().passOnBackToBottomPanel(controllerKey)
    if (result) {
      proxySelectionHelper.unselectAll()
    }

    return result
  }

  override fun onInsetsChanged() {
    val bottomPadding = with(appResources.composeDensity) {
      maxOf(
        globalWindowInsetsManager.bottom(),
        globalUiStateHolder.bottomPanel.bottomPanelHeight.value.roundToPx()
      )
    }

    val fabSizeDp = 64.dp
    val fabBottomMarginDp = 16.dp
    epoxyRecyclerView.additionalBottomPadding(fabSizeDp + fabBottomMarginDp)

    addProxyButton.updateMargins(bottom = dp(bottomPadding.toFloat()))
  }

  override fun showMessage(message: String) {
    showToast(message)
  }

  private fun onNewSelectionEvent(selectionEvent: BaseSelectionHelper.SelectionEvent) {
    when (selectionEvent) {
      is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      is BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        if (selectionEvent is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode) {
          requireBottomPanelContract().showBottomPanel(controllerKey, proxySelectionHelper.getBottomPanelMenus())
          addProxyButton.hide()
        }

        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        requireBottomPanelContract().hideBottomPanel(controllerKey)

        if (toolbarState.isInSelectionMode()) {
          toolbarState.pop()
        }

        addProxyButton.show()
      }
    }
  }

  override fun onMenuItemClicked(
    proxyMenuItemType: ProxySelectionHelper.ProxyMenuItemType,
    selectedItems: List<ProxyStorage.ProxyKey>
  ) {
    when (proxyMenuItemType) {
      ProxySelectionHelper.ProxyMenuItemType.Delete -> {
        val proxiesCount = selectedItems.size

        dialogFactory.createSimpleConfirmationDialog(
          context = context,
          titleText = getString(R.string.controller_proxy_setup_delete_selected_proxies_title, proxiesCount),
          negativeButtonText = getString(R.string.do_not),
          positiveButtonText = getString(R.string.delete),
          onPositiveButtonClickListener = {
            presenter.deleteProxies(selectedItems)
            proxySelectionHelper.unselectAll()
            showToast(R.string.controller_proxy_editor_proxy_deleted)
          }
        )
      }
    }
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
          state.proxyEntryViewList.forEachIndexed { index, proxyEntryView ->
            epoxyProxyView {
              id("epoxy_proxy_view_${proxyEntryView.proxyKeyString()}")
              proxySelectionHelper(proxySelectionHelper)
              proxyAddress(proxyEntryView.address)
              proxyPort(proxyEntryView.port.toString())
              proxyEnabled(proxyEntryView.enabled)
              proxySupportedSites(proxyEntryView.supportedSites)
              proxySupportedActions(proxyEntryView.supportedActions)
              proxyType(proxyEntryView.proxyType)
              proxySelection(proxyEntryView.selection)
              proxyHolderClickListener { onProxyItemViewClick(proxyEntryView) }
              proxyHolderLongClickListener { onProxyViewItemLongClick(proxyEntryView) }
              proxySettingsClickListener { onProxySettingsClick(proxyEntryView) }
            }

            if (index != state.proxyEntryViewList.lastIndex) {
              epoxyDividerView {
                id("epoxy_proxy_view_divider_${proxyEntryView.proxyKeyString()}")
                updateMargins(null)
              }
            }
          }
        }
      }
    }
  }

  private fun onProxyItemViewClick(proxyEntryView: ProxyEntryView) {
    if (proxySelectionHelper.isInSelectionMode()) {
      proxySelectionHelper.toggleSelection(proxyEntryView.proxyKey())
      return
    }

    presenter.toggleProxyEnableDisableState(proxyEntryView)
  }

  private fun onProxyViewItemLongClick(proxyEntryView: ProxyEntryView) {
    proxySelectionHelper.toggleSelection(proxyEntryView.proxyKey())
  }

  private fun onProxySettingsClick(proxyEntryView: ProxyEntryView) {
    if (proxySelectionHelper.isInSelectionMode()) {
      proxySelectionHelper.toggleSelection(proxyEntryView.proxyKey())
      return
    }

    val controller = ProxyEditorController(
      context,
      onApplyClickListener,
      proxyEntryView.proxyKey()
    )

    requireNavController().pushController(controller)
  }

  private fun showProxyEditingNotification() {
    if (PersistableChanState.proxyEditingNotificationShown.get()) {
      return
    }

    PersistableChanState.proxyEditingNotificationShown.set(true)

    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.controller_proxy_setup_title),
      descriptionText = getString(R.string.controller_proxy_setup_proxy_editing_notification)
    )
  }

  private fun enterSelectionModeOrUpdate() {
    val selectedItemsCount = proxySelectionHelper.selectedItemsCount()
    val totalItemsCount = (presenter.proxySetupState.value as? ProxySetupState.Data)?.proxyEntryViewList?.size ?: 0

    if (!toolbarState.isInSelectionMode()) {
      toolbarState.enterSelectionMode(
        leftItem = CloseMenuItem(
          onClick = {
            if (toolbarState.isInSelectionMode()) {
              toolbarState.pop()
              proxySelectionHelper.unselectAll()
            }
          }
        ),
        selectedItemsCount = selectedItemsCount,
        totalItemsCount = totalItemsCount,
      )
    }

    toolbarState.selection.updateCounters(
      selectedItemsCount = selectedItemsCount,
      totalItemsCount = totalItemsCount,
    )
  }

}