package com.github.adamantcheese.chan.features.archives

import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult

internal interface ArchivesSettingsControllerView {
    fun showToast(message: ArchivesSettingsPresenterMessage)
    fun onHistoryLoaded(history: List<ThirdPartyArchiveFetchResult>)
}