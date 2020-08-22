package com.github.adamantcheese.chan.features.setup

import android.content.Context
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.features.setup.data.SiteEnableState
import com.github.adamantcheese.chan.features.setup.data.SitesSetupControllerState
import com.github.adamantcheese.chan.features.setup.epoxy.EpoxySiteViewModel_
import com.github.adamantcheese.chan.features.setup.epoxy.epoxySiteView
import com.github.adamantcheese.chan.ui.epoxy.epoxyErrorView
import com.github.adamantcheese.chan.ui.epoxy.epoxyLoadingView
import com.github.adamantcheese.chan.ui.epoxy.epoxyTextView
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.plusAssign

class SitesSetupController(context: Context) : Controller(context), SitesSetupView {

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView

  private val controller = SitesEpoxyController()
  private val sitesPresenter = SitesSetupPresenter()

  override fun onCreate() {
    super.onCreate()

    view = AndroidUtils.inflate(context, R.layout.controller_sites_setup)
    navigation.title = "Sites"

    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    EpoxyTouchHelper
      .initDragging(controller)
      .withRecyclerView(epoxyRecyclerView)
      .forVerticalList()
      .withTarget(EpoxySiteViewModel_::class.java)
      .andCallbacks(object : EpoxyTouchHelper.DragCallbacks<EpoxySiteViewModel_>() {
        override fun onModelMoved(
          fromPosition: Int,
          toPosition: Int,
          modelBeingMoved: EpoxySiteViewModel_,
          itemView: View?
        ) {
          modelBeingMoved.siteDescriptor()?.let { siteDescriptor ->
            sitesPresenter.onSiteMoved(siteDescriptor, fromPosition, toPosition)
          }
        }
      })

    compositeDisposable += sitesPresenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    sitesPresenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    sitesPresenter.onDestroy()
  }

  private fun onStateChanged(state: SitesSetupControllerState) {
    controller.callback = {
      when (state) {
        SitesSetupControllerState.Loading -> {
          epoxyLoadingView {
            id("sites_setup_loading_view")
          }
        }
        SitesSetupControllerState.Empty -> {
          epoxyTextView {
            id("sites_setup_empty_text_view")
            message(context.getString(R.string.controller_sites_setup_no_sites))
          }
        }
        is SitesSetupControllerState.Error -> {
          epoxyErrorView {
            id("sites_setup_error_view")
            errorMessage(state.errorText)
          }
        }
        is SitesSetupControllerState.Data -> {
          state.siteCellDataList.forEach { siteCellData ->
            epoxySiteView {
              id("sites_setup_site_view_${siteCellData.siteDescriptor}")
              bindIcon(Pair(siteCellData.siteIcon, siteCellData.siteEnableState))
              bindSiteName(siteCellData.siteName)
              bindSwitch(siteCellData.siteEnableState)
              siteDescriptor(siteCellData.siteDescriptor)

              val callback = fun (enabled: Boolean) {
                if (siteCellData.siteEnableState == SiteEnableState.Disabled) {
                  showToast("Site is temporary or permanently disabled. It cannot be used.")
                  return
                }

                sitesPresenter.onSiteEnableStateChanged(siteCellData.siteDescriptor, enabled)
              }

              bindRowClickCallback(Pair(callback, siteCellData.siteEnableState))
              bindSettingClickCallback {
                navigationController!!.pushController(SiteSettingsController(context, siteCellData.siteDescriptor))
              }
            }
          }
        }
      }
    }

    controller.requestModelBuild()
  }

  private class SitesEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }
  }

  companion object {
    private const val TAG = "SitesSetupController"
  }
}