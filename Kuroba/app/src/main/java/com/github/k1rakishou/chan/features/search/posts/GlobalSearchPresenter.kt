package com.github.k1rakishou.chan.features.search.posts

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.concurrency.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.sites.search.SearchBoard
import com.github.k1rakishou.chan.features.search.posts.data.GlobalSearchControllerState
import com.github.k1rakishou.chan.features.search.posts.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.posts.data.SearchParameters
import com.github.k1rakishou.chan.features.search.posts.data.SelectedSite
import com.github.k1rakishou.chan.features.search.posts.data.SitesWithSearch
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class GlobalSearchPresenter(
  private val siteManager: SiteManager,
  private val themeEngine: ThemeEngine
) : BasePresenter<GlobalSearchView>() {

  private val _globalSearchControllerState =
    MutableStateFlow<GlobalSearchControllerState>(GlobalSearchControllerState.Uninitialized)
  val globalSearchControllerState: StateFlow<GlobalSearchControllerState>
    get() = _globalSearchControllerState

  private val searchResultsStateStorage = SearchResultsStateStorage

  private val searchUpdateExecutor = RendezvousCoroutineExecutor(scope = presenterScope)

  override fun onCreate(view: GlobalSearchView) {
    super.onCreate(view)

    presenterScope.launch {
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

      val searchResultsState = searchResultsStateStorage.searchResultsState
      if (searchResultsState != null) {
        withViewNormal {
          restoreSearchResultsController(
            searchInputState.sitesWithSearch.selectedSite.siteDescriptor,
            searchInputState.searchParameters
          )
        }
      }

      return true
    }

    return false
  }

  fun reloadCurrentState() {
    val currentStateData = (_globalSearchControllerState.value as? GlobalSearchControllerState.Data)?.data
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
        currentTheme = themeEngine.chanTheme.copyTheme(),
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
      if (site.configuration.globalSearchType != SiteConfiguration.GlobalSearchType.SearchNotSupported) {
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

    val site = siteManager.bySiteDescriptorAndActive(selectedSiteDescriptor)
    if (site == null) {
      setState(GlobalSearchControllerState.Error("Failed to find site for descriptor: ${selectedSiteDescriptor}"))
      return
    }

    val searchParameters = getDefaultSearchParameters(selectedSiteDescriptor)
    if (searchParameters == null) {
      setState(GlobalSearchControllerState.Error("Failed to create search parameters for site: ${selectedSiteDescriptor}"))
      return
    }

    val siteIconUrl = site.configuration.icon.url?.toString()
    val siteGlobalSearchConfig = site.configuration.globalSearchType

    val dataState = GlobalSearchControllerStateData(
      currentTheme = themeEngine.chanTheme.copyTheme(),
      sitesWithSearch = SitesWithSearch(
        sites = sitesSupportingSearch,
        selectedSite = SelectedSite(
          siteDescriptor = selectedSiteDescriptor,
          siteIconUrl = siteIconUrl,
          siteGlobalSearchType = siteGlobalSearchConfig
        )
      ),
      searchParameters = searchParameters
    )

    setState(GlobalSearchControllerState.Data(dataState))
  }

  private fun getDefaultSearchParameters(siteDescriptor: SiteDescriptor): SearchParameters? {
    val searchType = siteManager.bySiteDescriptorAndActive(siteDescriptor)?.configuration?.globalSearchType
      ?: return null

    when (searchType) {
      SiteConfiguration.GlobalSearchType.SearchNotSupported -> {
        error("Must not be used here")
      }
      SiteConfiguration.GlobalSearchType.SimpleQuerySearch,
      SiteConfiguration.GlobalSearchType.SimpleQueryBoardSearch -> {
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
      SiteConfiguration.GlobalSearchType.FuukaSearch -> {
        return SearchParameters.FuukaSearchParameters(
          query = "",
          subject = "",
          searchBoard = null
        )
      }
      SiteConfiguration.GlobalSearchType.FoolFuukaSearch -> {
        return SearchParameters.FoolFuukaSearchParameters(
          query = "",
          subject = "",
          searchBoard = null
        )
      }
    }
  }

  private fun setState(state: GlobalSearchControllerState) {
    _globalSearchControllerState.value = state
  }

  fun onSearchButtonClicked(selectedSite: SelectedSite, searchParameters: SearchParameters) {
    withViewNormal { openSearchResultsController(selectedSite.siteDescriptor, searchParameters) }
  }

  companion object {
    private const val TAG = "GlobalSearchPresenter"

    private var selectedSiteDescriptor: SiteDescriptor? = null
  }
}