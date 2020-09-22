package com.github.k1rakishou.chan.features.search

import android.content.Context
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerState
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SitesWithSearch
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchButtonView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchInputView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySiteSelectorSpinner
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import java.util.concurrent.atomic.AtomicReference

class GlobalSearchController(context: Context) : Controller(context), GlobalSearchView {

  private lateinit var epoxyRecyclerView: EpoxyRecyclerView
  private val presenter = GlobalSearchPresenter()
  private val inputViewRef = AtomicReference<View>(null)
  private var needSetInitialQuery = true

  override fun onCreate() {
    super.onCreate()

    navigation.title = AndroidUtils.getString(R.string.controller_search)
    navigation.swipeable = false

    view = AndroidUtils.inflate(context, R.layout.controller_global_search)
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
    needSetInitialQuery = false
    presenter.resetSavedState()
    return super.onBack()
  }

  override fun setNeedSetInitialQueryFlag() {
    needSetInitialQuery = true
  }

  override fun restoreSearchResultsController(siteDescriptor: SiteDescriptor, query: String) {
    inputViewRef.get()?.let { inputView -> AndroidUtils.hideKeyboard(inputView) }
    requireNavController().pushController(SearchResultsController(context, siteDescriptor, query), false)
  }

  override fun openSearchResultsController(siteDescriptor: SiteDescriptor, query: String) {
    inputViewRef.get()?.let { inputView -> AndroidUtils.hideKeyboard(inputView) }

    presenter.resetSearchResultsSavedState()
    requireNavController().pushController(SearchResultsController(context, siteDescriptor, query))
  }

  private fun onStateChanged(state: GlobalSearchControllerState) {
    epoxyRecyclerView.withModels {
      when (state) {
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
    epoxySiteSelectorSpinner {
      id("global_search_site_selector_spinner")
      sites(dataState.sitesWithSearch)
      onSiteSelectedListener { siteDescriptor ->
        presenter.reloadWithSelection(siteDescriptor, dataState.sitesWithSearch)
      }
    }

    val selectedSite = dataState.sitesWithSearch.selectedSite

    when (selectedSite.siteGlobalSearchType) {
      SiteGlobalSearchType.SimpleQuerySearch -> renderSimpleQuerySearch(dataState)
      SiteGlobalSearchType.SearchNotSupported -> return
    }

    val canRenderSearchButton = when (dataState) {
      is GlobalSearchControllerStateData.SitesSupportingSearchLoaded -> return
      is GlobalSearchControllerStateData.SearchQueryEntered -> {
        dataState.query.length >= GlobalSearchPresenter.MIN_SEARCH_QUERY_LENGTH
      }
    }

    if (!canRenderSearchButton) {
      return
    }

    renderSearchButton(dataState.sitesWithSearch, dataState.query)
  }

  private fun EpoxyController.renderSearchButton(sitesWithSearch: SitesWithSearch, query: String) {
    epoxySearchButtonView {
      id("global_search_button_view")
      currentQuery(query)
      onButtonClickListener { currentQuery ->
        presenter.onSearchButtonClicked(sitesWithSearch.selectedSite, currentQuery)
      }
    }
  }

  private fun EpoxyController.renderSimpleQuerySearch(dataState: GlobalSearchControllerStateData) {
    val sitesWithSearch = dataState.sitesWithSearch
    var initialQuery = (dataState as? GlobalSearchControllerStateData.SearchQueryEntered)?.query

    if (!needSetInitialQuery || dataState !is GlobalSearchControllerStateData.SearchQueryEntered) {
      initialQuery = null
    }

    epoxySearchInputView {
      id("global_search_input_view")
      initialQuery(initialQuery)
      onTextEnteredListener { query -> presenter.reloadWithSearchQuery(query, sitesWithSearch) }
      onBind { _, view, _ -> inputViewRef.set(view) }
      onUnbind { _, _ -> inputViewRef.set(null) }
    }
  }
}