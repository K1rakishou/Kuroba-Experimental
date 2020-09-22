package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

interface GlobalSearchView {
  fun openSearchResultsController(siteDescriptor: SiteDescriptor, query: String)
  fun restoreSearchResultsController(siteDescriptor: SiteDescriptor, query: String)
  fun setNeedSetInitialQueryFlag()
}