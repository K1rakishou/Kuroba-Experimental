package com.github.adamantcheese.chan.features.search

import com.github.adamantcheese.model.data.descriptor.SiteDescriptor

interface GlobalSearchView {
  fun openSearchResultsController(siteDescriptor: SiteDescriptor, query: String)
}