package com.github.k1rakishou.chan.features.filter_watches

import android.content.Context
import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherCoordinator
import com.github.k1rakishou.chan.features.bookmarks.BookmarksController
import com.github.k1rakishou.chan.features.bookmarks.epoxy.BaseThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.epoxyGridThreadBookmarkViewHolder
import com.github.k1rakishou.chan.ui.controller.navigation.TabPageController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxySimpleGroupView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.widget.KurobaSwipeRefreshLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class FilterWatchesController(
  context: Context,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : TabPageController(context), FilterWatchesControllerView, WindowInsetsListener {

  @Inject
  lateinit var filterWatcherCoordinator: FilterWatcherCoordinator
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private lateinit var swipeRefreshLayout: KurobaSwipeRefreshLayout

  private val presenter = FilterWatchesPresenter()
  private val controller = FilterWatchesEpoxyController()
  private val needRestoreScrollPosition = AtomicBoolean(true)

  private val topAdapterPosition: Int
    get() {
      val layoutManager = epoxyRecyclerView.layoutManager
      if (layoutManager == null) {
        return -1
      }

      when (layoutManager) {
        is GridLayoutManager -> return layoutManager.findFirstVisibleItemPosition()
        is LinearLayoutManager -> return layoutManager.findFirstVisibleItemPosition()
      }

      return -1
    }

  private val onScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE) {
        return
      }

      onRecyclerViewScrolled(recyclerView)
    }
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_filter_watches)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    swipeRefreshLayout = view.findViewById(R.id.filter_watches_swipe_refresh_layout)

    swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
      return@setOnChildScrollUpCallback epoxyRecyclerView.canScrollVertically(-1)
    }

    swipeRefreshLayout.setOnRefreshListener {
      filterWatcherCoordinator.restartFilterWatcherWithTinyDelay(isCalledBySwipeToRefresh = true)

      // The process of reloading filter watches may not notify us about the results when none of the
      // bookmarks were changed during the update so we need to have this timeout mechanism in
      // such case.
      mainScope.launch {
        delay(10_000)
        swipeRefreshLayout.isRefreshing = false
      }
    }

    epoxyRecyclerView.setController(controller)
    epoxyRecyclerView.addOnScrollListener(onScrollListener)

    mainScope.launch {
      presenter.listenForStateUpdates()
        .collect { filterWatchesControllerState -> renderState(filterWatchesControllerState) }
    }

    updateLayoutManager()
    onInsetsChanged()

    presenter.onCreate(this)
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::epoxyRecyclerView.isInitialized) {
      epoxyRecyclerView.removeOnScrollListener(onScrollListener)
      epoxyRecyclerView.clear()
    }

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    presenter.onDestroy()
  }

  override fun onInsetsChanged() {
    if (ChanSettings.isSplitLayoutMode()) {
      epoxyRecyclerView.updatePaddings(bottom = globalWindowInsetsManager.bottom())
    }
  }

  override fun rebuildNavigationItem(navigationItem: NavigationItem) {
    navigationItem.title = AppModuleAndroidUtils.getString(R.string.controller_filter_watches)
    navigationItem.swipeable = false
    navigationItem.hasDrawer = true
    navigationItem.hasBack = false
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
          swipeRefreshLayout.isRefreshing = false

          epoxyTextView {
            id("filter_watches_controller_empty_view")
            message(context.getString(R.string.no_filter_watched_threads))
          }
        }
        is FilterWatchesControllerState.Error -> {
          swipeRefreshLayout.isRefreshing = false

          epoxyErrorView {
            id("filter_watches_controller_error_view")
            errorMessage(filterWatchesControllerState.errorText)
          }
        }
        is FilterWatchesControllerState.Data -> {
          swipeRefreshLayout.isRefreshing = false
          renderDataState(filterWatchesControllerState)
        }
      }
    }

    epoxyRecyclerView.requestModelBuild()
  }

  private fun EpoxyController.renderDataState(filterWatchesControllerState: FilterWatchesControllerState.Data) {
    addOneshotModelBuildListener {
      if (needRestoreScrollPosition.compareAndSet(true, false)) {
        restoreScrollPosition()
      }
    }

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
    startActivityCallback.loadThread(threadDescriptor, animated = true)
  }

  private fun updateLayoutManager(forced: Boolean = false) {
    if (!forced && epoxyRecyclerView.layoutManager is GridLayoutManager) {
      return
    }

    val bookmarkWidth = ChanSettings.bookmarkGridViewWidth.get()
    val screenWidth = AndroidUtils.getDisplaySize(context).x
    val spanCount = (screenWidth / bookmarkWidth).coerceIn(
      BookmarksController.MIN_SPAN_COUNT,
      BookmarksController.MAX_SPAN_COUNT
    )

    epoxyRecyclerView.layoutManager = GridLayoutManager(context, spanCount).apply {
      spanSizeLookup = controller.spanSizeLookup
    }
  }

  private fun onRecyclerViewScrolled(recyclerView: RecyclerView) {
    val isGridLayoutManager = when (recyclerView.layoutManager) {
      is GridLayoutManager -> true
      is LinearLayoutManager -> false
      else -> throw IllegalStateException("Unknown layout manager: " +
        "${recyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    PersistableChanState.storeRecyclerIndexAndTopInfo(
      PersistableChanState.filterWatchesRecyclerIndexAndTop,
      isGridLayoutManager,
      RecyclerUtils.getIndexAndTop(recyclerView)
    )
  }

  private fun restoreScrollPosition() {
    val isForGridLayoutManager = when (epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> true
      is LinearLayoutManager -> false
      else -> throw IllegalStateException("Unknown layout manager: " +
        "${epoxyRecyclerView.layoutManager?.javaClass?.simpleName}"
      )
    }

    val indexAndTop = PersistableChanState.getRecyclerIndexAndTopInfo(
      PersistableChanState.filterWatchesRecyclerIndexAndTop,
      isForGridLayoutManager
    )

    when (val layoutManager = epoxyRecyclerView.layoutManager) {
      is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(indexAndTop.index, indexAndTop.top)
      is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(indexAndTop.index, indexAndTop.top)
    }
  }

  private inner class FilterWatchesEpoxyController : EpoxyController() {
    var callback: EpoxyController.() -> Unit = {}

    override fun buildModels() {
      callback(this)
    }
  }

}