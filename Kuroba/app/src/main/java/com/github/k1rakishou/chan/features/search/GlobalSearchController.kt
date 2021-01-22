package com.github.k1rakishou.chan.features.search

import android.content.Context
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerState
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SearchParameters
import com.github.k1rakishou.chan.features.search.data.SitesWithSearch
import com.github.k1rakishou.chan.features.search.epoxy.epoxyBoardSelectionButtonView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchButtonView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchInputView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchSiteView
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class GlobalSearchController(context: Context)
  : Controller(context),
  GlobalSearchView {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  private val presenter by lazy {
    GlobalSearchPresenter(siteManager)
  }

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private val inputViewRef = AtomicReference<View>(null)
  private var needSetInitialSearchParameters = true

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_search)
    navigation.swipeable = false

    view = inflate(context, R.layout.controller_global_search)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.swapAdapter(null, true)
    inputViewRef.set(null)
    presenter.onDestroy()
  }

  override fun onBack(): Boolean {
    needSetInitialSearchParameters = false
    presenter.resetSavedState()
    return super.onBack()
  }

  override fun setNeedSetInitialQueryFlag() {
    needSetInitialSearchParameters = true
  }

  override fun restoreSearchResultsController(
    siteDescriptor: SiteDescriptor,
    searchParameters: SearchParameters
  ) {
    inputViewRef.get()?.let { inputView -> AndroidUtils.hideKeyboard(inputView) }

    requireNavController().pushController(
      SearchResultsController(context, siteDescriptor, searchParameters),
      false
    )
  }

  override fun openSearchResultsController(
    siteDescriptor: SiteDescriptor,
    searchParameters: SearchParameters
  ) {
    inputViewRef.get()?.let { inputView -> AndroidUtils.hideKeyboard(inputView) }

    presenter.resetSearchResultsSavedState()
    requireNavController().pushController(
      SearchResultsController(context, siteDescriptor, searchParameters)
    )
  }

  private fun onStateChanged(state: GlobalSearchControllerState) {
    epoxyRecyclerView.withModels {
      when (state) {
        GlobalSearchControllerState.Uninitialized -> {
          // no-op
        }
        GlobalSearchControllerState.Loading -> {
          epoxyLoadingView {
            id("global_search_loading_view")
          }
        }
        GlobalSearchControllerState.Empty -> {
          epoxyTextView {
            id("global_search_empty_text_view")
            message(context.getString(R.string.controller_search_empty_sites))
          }
        }
        is GlobalSearchControllerState.Error -> {
          epoxyErrorView {
            id("global_search_error_view")
            errorMessage(state.errorText)
          }
        }
        is GlobalSearchControllerState.Data -> onDataStateChanged(state.data)
      }
    }
  }

  private fun EpoxyController.onDataStateChanged(dataState: GlobalSearchControllerStateData) {
    epoxySearchSiteView {
      id("global_search_epoxy_site")
      bindSiteName(dataState.sitesWithSearch.selectedSite.siteDescriptor.siteName)
      bindIcon(dataState.sitesWithSearch.selectedSite.siteIconUrl)
      bindClickCallback {
        val controller = SelectSiteForSearchController(
          context = context,
          selectedSite = dataState.sitesWithSearch.selectedSite.siteDescriptor,
          onSiteSelected = { selectedSiteDescriptor -> presenter.onSearchSiteSelected(selectedSiteDescriptor) }
        )

        requireNavController().presentController(controller)
      }
    }

    val selectedSite = dataState.sitesWithSearch.selectedSite

    val canRenderSearchButton = when (selectedSite.siteGlobalSearchType) {
      SiteGlobalSearchType.SimpleQuerySearch -> renderSimpleQuerySearch(dataState)
      SiteGlobalSearchType.FoolFuukaSearch -> renderFoolFuukaSearch(dataState)
      SiteGlobalSearchType.SearchNotSupported -> false
    }

    if (!canRenderSearchButton) {
      return
    }

    renderSearchButton(dataState.sitesWithSearch, dataState.searchParameters)
  }

  private fun EpoxyController.renderSearchButton(
    sitesWithSearch: SitesWithSearch,
    searchParameters: SearchParameters
  ) {
    epoxySearchButtonView {
      id("global_search_button_view")
      onButtonClickListener {
        presenter.onSearchButtonClicked(sitesWithSearch.selectedSite, searchParameters)
      }
    }
  }

  private fun EpoxyController.renderFoolFuukaSearch(dataState: GlobalSearchControllerStateData): Boolean {
    val sitesWithSearch = dataState.sitesWithSearch
    val searchParameters = dataState.searchParameters as SearchParameters.FoolFuukaSearchParameters
    val selectedSiteDescriptor = sitesWithSearch.selectedSite.siteDescriptor

    var initialQuery = searchParameters.query
    var selectedBoard = searchParameters.boardDescriptor
    var selectedBoardCode = selectedBoard?.boardCode

    if (!needSetInitialSearchParameters) {
      initialQuery = ""
      selectedBoard = null
      selectedBoardCode = null
    }

    epoxyBoardSelectionButtonView {
      id("global_search_board_selection_button_view")
      boardCode(selectedBoardCode)
      bindClickCallback {
        val boardsSupportingSearch = archivesManager.getBoardsSupportingSearch(selectedSiteDescriptor)
        if (boardsSupportingSearch.isEmpty()) {
          return@bindClickCallback
        }

        // TODO(KurobaEx): show board selection menu
        val updatedSearchParameters = SearchParameters.FoolFuukaSearchParameters(
          query = initialQuery,
          boardDescriptor = boardsSupportingSearch.first()
        )

        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
    }

    epoxySearchInputView {
      id("global_search_fool_fuuka_search_input_view")
      initialQuery(initialQuery)
      onTextEnteredListener { query ->
        val updatedSearchParameters = SearchParameters.FoolFuukaSearchParameters(
          query = query,
          boardDescriptor = selectedBoard
        )

        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> inputViewRef.set(view) }
      onUnbind { _, _ -> inputViewRef.set(null) }
    }

    return initialQuery.length >= GlobalSearchPresenter.MIN_SEARCH_QUERY_LENGTH
      && selectedBoard != null
  }

  private fun EpoxyController.renderSimpleQuerySearch(dataState: GlobalSearchControllerStateData): Boolean {
    val sitesWithSearch = dataState.sitesWithSearch
    val searchParameters = dataState.searchParameters as SearchParameters.SimpleQuerySearchParameters
    var initialQuery = searchParameters.query

    if (!needSetInitialSearchParameters) {
      initialQuery = ""
    }

    epoxySearchInputView {
      id("global_search_simple_query_search_view")
      initialQuery(initialQuery)
      onTextEnteredListener { query ->
        val updatedSearchParameters = SearchParameters.SimpleQuerySearchParameters(query)
        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> inputViewRef.set(view) }
      onUnbind { _, _ -> inputViewRef.set(null) }
    }

    return initialQuery.length >= GlobalSearchPresenter.MIN_SEARCH_QUERY_LENGTH
  }
}