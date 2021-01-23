package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.chan.features.search.data.SearchParameters
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

interface GlobalSearchView {
  fun openSearchResultsController(siteDescriptor: SiteDescriptor, searchParameters: SearchParameters)
  fun restoreSearchResultsController(siteDescriptor: SiteDescriptor, searchParameters: SearchParameters)
  fun updateResetSearchParametersFlag(reset: Boolean)
}