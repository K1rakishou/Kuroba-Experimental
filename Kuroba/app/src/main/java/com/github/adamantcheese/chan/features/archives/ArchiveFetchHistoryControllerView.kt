package com.github.adamantcheese.chan.features.archives

import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult

interface ArchiveFetchHistoryControllerView {
  fun rebuildFetchResultsList(fetchResultList: List<ThirdPartyArchiveFetchResult>)
  fun onFetchResultListChanged()
  fun showDeleteErrorToast()
  fun showUnknownErrorToast()
  fun popController()
}