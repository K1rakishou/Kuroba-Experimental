package com.github.k1rakishou.chan.features.setup

import android.annotation.SuppressLint
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyModelTouchCallback
import com.airbnb.epoxy.EpoxyViewHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.features.setup.data.SitesSetupControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.site.EpoxySiteView
import com.github.k1rakishou.chan.features.setup.epoxy.site.EpoxySiteViewModel_
import com.github.k1rakishou.chan.features.setup.epoxy.site.epoxySiteView
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.plusAssign

class SitesSetupController(context: Context) : Controller(context), SitesSetupView {

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private val controller = SitesEpoxyController()
  private val sitesPresenter = SitesSetupPresenter()
  private lateinit var itemTouchHelper: ItemTouchHelper

  private val touchHelperCallback = object : EpoxyModelTouchCallback<EpoxySiteViewModel_>(
    controller,
    EpoxySiteViewModel_::class.java
  ) {
    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlagsForModel(model: EpoxySiteViewModel_?, adapterPosition: Int): Int {
      return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onDragStarted(model: EpoxySiteViewModel_?, itemView: View?, adapterPosition: Int) {
      itemView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onModelMoved(
      fromPosition: Int,
      toPosition: Int,
      modelBeingMoved: EpoxySiteViewModel_?,
      itemView: View?
    ) {
      sitesPresenter.onSiteMoving(fromPosition, toPosition)
    }

    override fun onDragReleased(model: EpoxySiteViewModel_?, itemView: View?) {
      sitesPresenter.onSiteMoved()
    }
  }

  override fun onCreate() {
    super.onCreate()

    view = AndroidUtils.inflate(context, R.layout.controller_sites_setup)
    navigation.title = context.getString(R.string.controller_sites_title)

    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    itemTouchHelper = ItemTouchHelper(touchHelperCallback)
    itemTouchHelper.attachToRecyclerView(epoxyRecyclerView)

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

              val callback = fun(enabled: Boolean) {
                if (siteCellData.siteEnableState == SiteEnableState.Disabled) {
                  showToast("Site is temporary or permanently disabled. It cannot be used.")
                  return
                }

                sitesPresenter.onSiteEnableStateChanged(siteCellData.siteDescriptor, enabled)
              }

              bindRowClickCallback(Pair(callback, siteCellData.siteEnableState))
              bindSettingClickCallback {
                navigationController!!.pushController(
                  SiteSettingsController(
                    context,
                    siteCellData.siteDescriptor
                  )
                )
              }
            }
          }
        }
      }
    }

    controller.requestModelBuild()
  }

  private inner class SitesEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewAttachedToWindow(holder: EpoxyViewHolder, model: EpoxyModel<*>) {
      val itemView = holder.itemView

      if (itemView is EpoxySiteView) {
        itemView.siteReorder.setOnTouchListener { v, event ->
          if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            itemTouchHelper.startDrag(holder)
          }

          return@setOnTouchListener false
        }
      }
    }
  }

  companion object {
    private const val TAG = "SitesSetupController"
  }
}