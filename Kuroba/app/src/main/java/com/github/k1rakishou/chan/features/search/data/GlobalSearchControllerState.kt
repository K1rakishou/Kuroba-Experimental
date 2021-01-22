package com.github.k1rakishou.chan.features.search.data

import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.chan.features.search.GlobalSearchPresenter
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

internal sealed class GlobalSearchControllerState {
  object Uninitialized : GlobalSearchControllerState()
  object Loading : GlobalSearchControllerState()
  object Empty : GlobalSearchControllerState()
  data class Error(val errorText: String) : GlobalSearchControllerState()
  data class Data(val data: GlobalSearchControllerStateData) : GlobalSearchControllerState()
}

internal data class SelectedSite(
  val siteDescriptor: SiteDescriptor,
  val siteIconUrl: String,
  val siteGlobalSearchType: SiteGlobalSearchType
)

internal data class SitesWithSearch(
  val sites: List<SiteDescriptor>,
  val selectedSite: SelectedSite
)

internal data class GlobalSearchControllerStateData(
  val sitesWithSearch: SitesWithSearch,
  val searchParameters: SearchParameters
)

sealed class SearchParameters {
  abstract val query: String
  abstract fun checkValid()

  data class SimpleQuerySearchParameters(
    override val query: String
  ) : SearchParameters() {

    override fun checkValid() {
      check(query.length >= GlobalSearchPresenter.MIN_SEARCH_QUERY_LENGTH) { "Bad query: $query" }
    }

  }

  data class FoolFuukaSearchParameters(
    override val query: String,
    val boardDescriptor: BoardDescriptor?
  ) : SearchParameters() {

    override fun checkValid() {
      check(query.length >= GlobalSearchPresenter.MIN_SEARCH_QUERY_LENGTH) { "Bad query: $query" }
      checkNotNull(boardDescriptor) { "boardDescriptor is null" }
    }

  }
}