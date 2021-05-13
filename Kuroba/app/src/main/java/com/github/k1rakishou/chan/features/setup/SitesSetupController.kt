package com.github.k1rakishou.chan.features.setup

import android.annotation.SuppressLint
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.EpoxyModelTouchCallback
import com.airbnb.epoxy.EpoxyViewHolder
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.features.setup.data.SitesSetupControllerState
import com.github.k1rakishou.chan.features.setup.epoxy.site.EpoxySiteView
import com.github.k1rakishou.chan.features.setup.epoxy.site.EpoxySiteViewModel_
import com.github.k1rakishou.chan.features.setup.epoxy.site.epoxySiteView
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyExpandableGroupView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import javax.inject.Inject

class SitesSetupController(context: Context) : Controller(context), SitesSetupView {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  private val sitesPresenter by lazy {
    SitesSetupPresenter(
      siteManager = siteManager,
      archivesManager = archivesManager
    )
  }

  private val controller = SitesEpoxyController()
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
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

    override fun canDropOver(
      recyclerView: RecyclerView,
      current: EpoxyViewHolder,
      target: EpoxyViewHolder
    ): Boolean {
      val currentView = current.itemView as? EpoxySiteView
      val targetView = target.itemView as? EpoxySiteView

      if (currentView == null || targetView == null) {
        return false
      }

      if (targetView.isArchiveSite) {
        return false
      }

      return true
    }

    override fun onDragStarted(model: EpoxySiteViewModel_?, itemView: View?, adapterPosition: Int) {
      itemView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: EpoxyViewHolder,
      target: EpoxyViewHolder
    ): Boolean {
      val fromPosition = viewHolder.adapterPosition
      val toPosition = target.adapterPosition

      val fromSiteDescriptor = (viewHolder.model as? EpoxySiteViewModel_)?.siteDescriptor()
      val toSiteDescriptor = (target.model as? EpoxySiteViewModel_)?.siteDescriptor()

      if (fromSiteDescriptor == null || toSiteDescriptor == null) {
        return false
      }

      sitesPresenter.onSiteMoving(fromSiteDescriptor, toSiteDescriptor)
      controller.moveModel(fromPosition, toPosition)

      return true
    }

    override fun onDragReleased(model: EpoxySiteViewModel_?, itemView: View?) {
      sitesPresenter.onSiteMoved()
    }

  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_sites_setup)
    navigation.title = context.getString(R.string.controller_sites_title)

    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    itemTouchHelper = ItemTouchHelper(touchHelperCallback)
    itemTouchHelper.attachToRecyclerView(epoxyRecyclerView)

    compositeDisposable.add(
      sitesPresenter.listenForStateChanges()
        .subscribe { state -> onStateChanged(state) }
    )

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
            val siteCellArchiveGroupInfo = siteCellData.siteCellArchiveGroupInfo

            // Render sites
            renderSite(siteCellData)

            if (siteCellArchiveGroupInfo != null) {
              val archivesInfoText = context.getString(
                R.string.controller_sites_setup_archives_group,
                siteCellArchiveGroupInfo.archiveEnabledTotalCount.enabledCount,
                siteCellArchiveGroupInfo.archiveEnabledTotalCount.totalCount,
              )

              // Render archives for this site
              epoxyExpandableGroupView {
                id("sites_setup_site_view_archive_toggle_${siteCellData.siteDescriptor}")
                isExpanded(siteCellArchiveGroupInfo.isGroupExpanded)
                groupTitle(archivesInfoText)
                clickListener { sitesPresenter.toggleGroupCollapseState(siteCellData.siteDescriptor) }
              }

              if (siteCellArchiveGroupInfo.isGroupExpanded) {
                siteCellArchiveGroupInfo.archives.forEach { siteArchiveCellData ->
                  renderArchiveSite(siteArchiveCellData)
                }
              }
            }
          }
        }
      }
    }

    controller.requestModelBuild()
  }

  private fun EpoxyController.renderSite(siteCellData: SiteCellData) {
    epoxySiteView {
      id("sites_setup_site_view_${siteCellData.siteDescriptor}")
      bindIcon(Pair(siteCellData.siteIcon, siteCellData.siteEnableState))
      bindSiteName(siteCellData.siteName)
      bindSwitch(siteCellData.siteEnableState)
      siteDescriptor(siteCellData.siteDescriptor)
      isArchiveSite(false)

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

  private fun EpoxyController.renderArchiveSite(siteArchiveCellData: SiteCellData) {
    epoxySiteView {
      id("sites_setup_site_view_${siteArchiveCellData.siteDescriptor}")
      bindIcon(
        Pair(
          siteArchiveCellData.siteIcon,
          siteArchiveCellData.siteEnableState
        )
      )
      bindSiteName(siteArchiveCellData.siteName)
      bindSwitch(siteArchiveCellData.siteEnableState)
      siteDescriptor(siteArchiveCellData.siteDescriptor)
      isArchiveSite(true)

      val callback = fun(enabled: Boolean) {
        if (siteArchiveCellData.siteEnableState == SiteEnableState.Disabled) {
          showToast("Site is temporary or permanently disabled. It cannot be used.")
          return
        }

        sitesPresenter.onSiteEnableStateChanged(
          siteArchiveCellData.siteDescriptor,
          enabled
        )
      }

      bindRowClickCallback(Pair(callback, siteArchiveCellData.siteEnableState))
      bindSettingClickCallback {
        navigationController!!.pushController(
          SiteSettingsController(
            context,
            siteArchiveCellData.siteDescriptor
          )
        )
      }
    }
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