package com.github.k1rakishou.chan.features.search.posts

import com.github.k1rakishou.chan.features.search.posts.data.SearchParameters
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

interface GlobalSearchView {
  fun openSearchResultsController(siteDescriptor: SiteDescriptor, searchParameters: SearchParameters)
  fun restoreSearchResultsController(siteDescriptor: SiteDescriptor, searchParameters: SearchParameters)
  fun updateResetSearchParametersFlag(reset: Boolean)
}