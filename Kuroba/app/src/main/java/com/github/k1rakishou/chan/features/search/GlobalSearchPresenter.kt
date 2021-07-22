package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.search.SearchBoard
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerState
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SearchParameters
import com.github.k1rakishou.chan.features.search.data.SelectedSite
import com.github.k1rakishou.chan.features.search.data.SitesWithSearch
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class GlobalSearchPresenter(
  private val siteManager: SiteManager,
  private val themeEngine: ThemeEngine
) : BasePresenter<GlobalSearchView>() {

  private val globalSearchControllerStateSubject =
    BehaviorProcessor.createDefault<GlobalSearchControllerState>(GlobalSearchControllerState.Uninitialized)
  private val searchResultsStateStorage = SearchResultsStateStorage

  private val searchUpdateExecutor = RendezvousCoroutineExecutor(scope = scope)

  override fun onCreate(view: GlobalSearchView) {
    super.onCreate(view)

    scope.launch {
      if (searchResultsStateStorage.searchInputState != null) {
        if (tryRestorePrevState()) {
          return@launch
        }

        // fallthrough
      }

      val loadingStateCancellationJob = launch {
        delay(50)
        setState(GlobalSearchControllerState.Loading)
      }

      siteManager.awaitUntilInitialized()
      reloadSearchState(loadingStateCancellationJob)
    }
  }

  private fun tryRestorePrevState(): Boolean {
    val searchInputState = searchResultsStateStorage.searchInputState!!

    if (searchInputState.searchParameters.isValid()) {
      setState(GlobalSearchControllerState.Data(searchInputState))

      withViewNormal {
        restoreSearchResultsController(
          searchInputState.sitesWithSearch.selectedSite.siteDescriptor,
          searchInputState.searchParameters
        )
      }

      return true
    }

    return false
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

  fun reloadCurrentState() {
    val currentStateData = (globalSearchControllerStateSubject.value as? GlobalSearchControllerState.Data)?.data
      ?: return

    val newDataState = GlobalSearchControllerState.Data(
      currentStateData.copy(currentTheme = themeEngine.chanTheme)
    )

    setState(newDataState)
  }

  fun resetSavedState() {
    searchResultsStateStorage.resetSearchInputState()
  }

  fun resetSearchResultsSavedState() {
    searchResultsStateStorage.resetSearchResultState()
  }

  fun reloadWithSearchParameters(searchParameters: SearchParameters?, sitesWithSearch: SitesWithSearch) {
    searchUpdateExecutor.post {
      if (searchParameters == null) {
        selectedSiteDescriptor = null

        withView { updateResetSearchParametersFlag(true) }
        searchResultsStateStorage.resetSearchInputState()

        reloadSearchState(null)
        return@post
      }

      val dataState = GlobalSearchControllerStateData(
        currentTheme = themeEngine.chanTheme.fullCopy(),
        sitesWithSearch = sitesWithSearch,
        searchParameters = searchParameters
      )

      setState(GlobalSearchControllerState.Data(dataState))
      searchResultsStateStorage.updateSearchInputState(dataState)

      withView { updateResetSearchParametersFlag(false) }
    }
  }

  fun onSearchSiteSelected(newSelectedSiteDescriptor: SiteDescriptor) {
    searchUpdateExecutor.post {
      selectedSiteDescriptor = newSelectedSiteDescriptor

      searchResultsStateStorage.resetSearchInputState()
      withView { updateResetSearchParametersFlag(true) }

      reloadSearchState(null)
    }
  }

  private fun reloadSearchState(loadingStateCancellationJob: Job?) {
    val sitesSupportingSearch = mutableListOf<SiteDescriptor>()

    siteManager.viewActiveSitesOrderedWhile { chanSiteData, site ->
      if (site.siteGlobalSearchType() != SiteGlobalSearchType.SearchNotSupported) {
        sitesSupportingSearch += chanSiteData.siteDescriptor
      }

      return@viewActiveSitesOrderedWhile true
    }

    if (loadingStateCancellationJob != null && !loadingStateCancellationJob.isCancelled) {
      loadingStateCancellationJob.cancel()
    }

    if (sitesSupportingSearch.isEmpty()) {
      setState(GlobalSearchControllerState.Empty)
      return
    }

    val selectedSiteDescriptor = selectedSiteDescriptor
      ?: sitesSupportingSearch.first()

    val site = siteManager.bySiteDescriptor(selectedSiteDescriptor)
    if (site == null) {
      setState(GlobalSearchControllerState.Error("Failed to find site for descriptor: ${selectedSiteDescriptor}"))
      return
    }

    val searchParameters = getDefaultSearchParameters(selectedSiteDescriptor)
    if (searchParameters == null) {
      setState(GlobalSearchControllerState.Error("Failed to create search parameters for site: ${selectedSiteDescriptor}"))
      return
    }

    val siteIcon = site.icon()
    val searchType = site.siteGlobalSearchType()

    val dataState = GlobalSearchControllerStateData(
      currentTheme = themeEngine.chanTheme.fullCopy(),
      sitesWithSearch = SitesWithSearch(
        sitesSupportingSearch,
        SelectedSite(selectedSiteDescriptor, siteIcon, searchType)
      ),
      searchParameters = searchParameters
    )

    setState(GlobalSearchControllerState.Data(dataState))
  }

  private fun getDefaultSearchParameters(siteDescriptor: SiteDescriptor): SearchParameters? {
    val searchType = siteManager.bySiteDescriptor(siteDescriptor)?.siteGlobalSearchType()
      ?: return null

    when (searchType) {
      SiteGlobalSearchType.SearchNotSupported -> {
        throw IllegalStateException("Must not be used here")
      }
      SiteGlobalSearchType.SimpleQuerySearch,
      SiteGlobalSearchType.SimpleQueryBoardSearch -> {
        if (siteDescriptor.is4chan()) {
          return SearchParameters.Chan4SearchParams(
            query = "",
            searchBoard = SearchBoard.AllBoards
          )
        } else if (siteDescriptor.isDvach()) {
          return SearchParameters.DvachSearchParams(
            query = "",
            searchBoard = null
          )
        }

        throw IllegalArgumentException("Unsupported site: $siteDescriptor")
      }
      SiteGlobalSearchType.FuukaSearch -> {
        return SearchParameters.FuukaSearchParameters(
          query = "",
          subject = "",
          searchBoard = null
        )
      }
      SiteGlobalSearchType.FoolFuukaSearch -> {
        return SearchParameters.FoolFuukaSearchParameters(
          query = "",
          subject = "",
          searchBoard = null
        )
      }
    }
  }

  private fun setState(state: GlobalSearchControllerState) {
    globalSearchControllerStateSubject.onNext(state)
  }

  fun onSearchButtonClicked(selectedSite: SelectedSite, searchParameters: SearchParameters) {
    withViewNormal { openSearchResultsController(selectedSite.siteDescriptor, searchParameters) }
  }

  companion object {
    private const val TAG = "GlobalSearchPresenter"

    private var selectedSiteDescriptor: SiteDescriptor? = null
  }
}