package com.github.k1rakishou.chan.features.filter_watches

import android.content.Context
import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.bookmarks.BookmarksController
import com.github.k1rakishou.chan.features.bookmarks.epoxy.BaseThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.epoxyGridThreadBookmarkViewHolder
import com.github.k1rakishou.chan.ui.controller.navigation.TabPageController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxySimpleGroupView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FilterWatchesController(
  context: Context,
) : TabPageController(context), FilterWatchesControllerView {
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private val presenter = FilterWatchesPresenter()
  private val controller = FilterWatchesEpoxyController()

  private lateinit var threadLoadCoroutineExecutor: SerializedCoroutineExecutor

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    threadLoadCoroutineExecutor = SerializedCoroutineExecutor(mainScope)

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_filter_watches)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    epoxyRecyclerView.setController(controller)

    mainScope.launch {
      presenter.listenForStateUpdates()
        .collect { filterWatchesControllerState -> renderState(filterWatchesControllerState) }
    }

    updateLayoutManager()
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

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    updateLayoutManager(forced = true)
  }

  private fun renderState(filterWatchesControllerState: FilterWatchesControllerState) {
    controller.callback = {
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

    epoxyRecyclerView.requestModelBuild()
  }

  private fun EpoxyController.renderDataState(filterWatchesControllerState: FilterWatchesControllerState.Data) {
    val isTablet = AppModuleAndroidUtils.isTablet()

    filterWatchesControllerState.groupedFilterWatches.forEach { groupOfFilterWatches ->
      epoxySimpleGroupView {
        id("epoxy_simple_group_view_${groupOfFilterWatches.filterPattern.hashCode()}")
        groupTitle(groupOfFilterWatches.filterPattern)
        clickListener(null)
        longClickListener(null)
      }

      groupOfFilterWatches.filterWatches.forEach { filterWatch ->
        val requestData =
          BaseThreadBookmarkViewHolder.ImageLoaderRequestData(filterWatch.thumbnailUrl)

        epoxyGridThreadBookmarkViewHolder {
          id("thread_grid_bookmark_view_${filterWatch.threadDescriptor.serializeToString()}")
          context(context)
          imageLoaderRequestData(requestData)
          threadDescriptor(filterWatch.threadDescriptor)
          titleString(filterWatch.title)
          threadBookmarkStats(filterWatch.threadBookmarkStats)
          threadBookmarkSelection(null)
          highlightBookmark(filterWatch.highlight)
          isTablet(isTablet)
          groupId(null)
          reorderingMode(false)
          bookmarkClickListener { onBookmarkClicked(filterWatch.threadDescriptor) }
          bookmarkLongClickListener(null)
          bookmarkStatsClickListener(null)
        }
      }
    }
  }

  private fun onBookmarkClicked(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    threadLoadCoroutineExecutor.post {
      (context as? StartActivity)?.loadThread(threadDescriptor, true)
    }
  }

  private fun updateLayoutManager(forced: Boolean = false) {
    if (!forced && epoxyRecyclerView.layoutManager is GridLayoutManager) {
      return
    }

    val bookmarkWidth = ChanSettings.bookmarkGridViewWidth.get()
    val screenWidth = AndroidUtils.getDisplaySize().x
    val spanCount = (screenWidth / bookmarkWidth).coerceIn(
      BookmarksController.MIN_SPAN_COUNT,
      BookmarksController.MAX_SPAN_COUNT
    )

    epoxyRecyclerView.layoutManager = GridLayoutManager(context, spanCount).apply {
      spanSizeLookup = controller.spanSizeLookup
    }
  }

  private inner class FilterWatchesEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }
  }

}