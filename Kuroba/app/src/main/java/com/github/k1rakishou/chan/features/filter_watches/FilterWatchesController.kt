package com.github.k1rakishou.chan.features.filter_watches

import android.content.Context
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.navigation.TabPageController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxySimpleGroupView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FilterWatchesController(
  context: Context,
) : TabPageController(context), FilterWatchesControllerView {
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private val presenter = FilterWatchesPresenter()

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_filter_watches)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    mainScope.launch {
      presenter.listenForStateUpdates()
        .collect { filterWatchesControllerState -> renderState(filterWatchesControllerState) }
    }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
  }

  override fun rebuildNavigationItem(navigationItem: NavigationItem) {
    navigationItem.title = AppModuleAndroidUtils.getString(R.string.controller_filter_watches)
    navigationItem.swipeable = false
  }

  override fun onTabFocused() {
    // no-op
  }

  override fun canSwitchTabs(): Boolean {
    return true
  }

  private fun renderState(filterWatchesControllerState: FilterWatchesControllerState) {
    epoxyRecyclerView.withModels {
      when (filterWatchesControllerState) {
        FilterWatchesControllerState.Loading -> {
          epoxyLoadingView {
            id("filter_watches_controller_loading_view")
          }
        }
        FilterWatchesControllerState.Empty -> {
          epoxyTextView {
            id("filter_watches_controller_empty_view")
            message(context.getString(R.string.no_filter_watched_threads))
          }
        }
        is FilterWatchesControllerState.Error -> {
          epoxyErrorView {
            id("filter_watches_controller_error_view")
            errorMessage(filterWatchesControllerState.errorText)
          }
        }
        is FilterWatchesControllerState.Data -> renderDataState(filterWatchesControllerState)
      }
    }
  }

  private fun EpoxyController.renderDataState(filterWatchesControllerState: FilterWatchesControllerState.Data) {
    filterWatchesControllerState.groupedFilterWatches.forEach { groupOfFilterWatches ->
      epoxySimpleGroupView {
        id("epoxy_simple_group_view_${groupOfFilterWatches.filterPattern.hashCode()}")
        groupTitle(groupOfFilterWatches.filterPattern)
        clickListener(null)
        longClickListener(null)
      }

      groupOfFilterWatches.filterWatches.forEach { filterWatch ->
        // TODO(KurobaEx v0.5.0):
      }
    }
  }

}