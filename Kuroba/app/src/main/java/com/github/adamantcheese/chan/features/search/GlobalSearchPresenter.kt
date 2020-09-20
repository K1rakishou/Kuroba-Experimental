package com.github.adamantcheese.chan.features.search

import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.base.SuspendDebouncer
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.adamantcheese.chan.features.search.data.GlobalSearchControllerState
import com.github.adamantcheese.chan.features.search.data.GlobalSearchControllerStateData
import com.github.adamantcheese.chan.features.search.data.SelectedSite
import com.github.adamantcheese.chan.features.search.data.SitesWithSearch
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.errorMessageOrClassName
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class GlobalSearchPresenter : BasePresenter<GlobalSearchView>() {

  @Inject
  lateinit var siteManager: SiteManager

  private val globalSearchControllerStateSubject =
    BehaviorProcessor.createDefault<GlobalSearchControllerState>(GlobalSearchControllerState.Loading)

  private val queryEnterDebouncer = SuspendDebouncer(scope)

  override fun onCreate(view: GlobalSearchView) {
    super.onCreate(view)

    Chan.inject(this)

    scope.launch {
      siteManager.awaitUntilInitialized()
      loadDefaultSearchState()
    }
  }

  fun listenForStateChanges(): Flowable<GlobalSearchControllerState> {
    return globalSearchControllerStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to globalSearchControllerStateSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> GlobalSearchControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  fun reloadWithSelection(siteDescriptor: SiteDescriptor, sitesWithSearch: SitesWithSearch) {
    val selectedSiteDescriptor = sitesWithSearch.selectedSite.siteDescriptor
    if (selectedSiteDescriptor == siteDescriptor) {
      return
    }

    val searchType = siteManager.bySiteDescriptor(selectedSiteDescriptor)?.siteGlobalSearchType()
      ?: SiteGlobalSearchType.SearchNotSupported

    val dataState = GlobalSearchControllerStateData.SitesSupportingSearchLoaded(
      sitesWithSearch.copy(selectedSite = SelectedSite(selectedSiteDescriptor, searchType))
    )

    setState(GlobalSearchControllerState.Data(dataState))
  }

  fun reloadWithSearchQuery(query: String, sitesWithSearch: SitesWithSearch) {
    if (query.length < MIN_SEARCH_QUERY_LENGTH) {
      return
    }

    val prevDataState = (globalSearchControllerStateSubject.value as? GlobalSearchControllerState.Data)?.data
    if (prevDataState is GlobalSearchControllerStateData.SearchQueryEntered) {
      if (prevDataState.query == query) {
        return
      }
    }

    queryEnterDebouncer.post(DEBOUNCE_TIMEOUT_MS) {
      val dataState = GlobalSearchControllerStateData.SearchQueryEntered(
        sitesWithSearch,
        query
      )

      setState(GlobalSearchControllerState.Data(dataState))
    }
  }

  private fun loadDefaultSearchState() {
    val sitesSupportingSearch = mutableListOf<SiteDescriptor>()

    siteManager.viewActiveSitesOrdered { chanSiteData, site ->
      if (site.siteGlobalSearchType() != SiteGlobalSearchType.SearchNotSupported) {
        sitesSupportingSearch += chanSiteData.siteDescriptor
      }

      return@viewActiveSitesOrdered true
    }

    if (sitesSupportingSearch.isEmpty()) {
      setState(GlobalSearchControllerState.Empty)
      return
    }

    val selectedSiteDescriptor = sitesSupportingSearch.first()

    val searchType = siteManager.bySiteDescriptor(selectedSiteDescriptor)?.siteGlobalSearchType()
      ?: SiteGlobalSearchType.SearchNotSupported

    val dataState = GlobalSearchControllerStateData.SitesSupportingSearchLoaded(
      SitesWithSearch(
        sitesSupportingSearch,
        SelectedSite(selectedSiteDescriptor, searchType)
      )
    )

    setState(GlobalSearchControllerState.Data(dataState))
  }

  private fun setState(state: GlobalSearchControllerState) {
    globalSearchControllerStateSubject.onNext(state)
  }

  fun onSearchButtonClicked(selectedSite: SelectedSite, query: String) {
    withViewNormal { openSearchResultsController(selectedSite.siteDescriptor, query) }
  }

  companion object {
    private const val TAG = "GlobalSearchPresenter"

    const val MIN_SEARCH_QUERY_LENGTH = 2
    private const val DEBOUNCE_TIMEOUT_MS = 150L
  }
}