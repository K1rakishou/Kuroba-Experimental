package com.github.adamantcheese.chan.features.archives

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class ArchiveFetchHistoryPresenter(
        private val fetchResultList: MutableList<ThirdPartyArchiveFetchResult>
) : BasePresenter<ArchiveFetchHistoryControllerView>() {

    @Inject
    lateinit var archivesManager: ArchivesManager

    private val mutex = Mutex()

    override fun onCreate(view: ArchiveFetchHistoryControllerView) {
        super.onCreate(view)

        inject(this)

        scope.launch {
            archivesManager.listenForFetchHistoryChanges()
                    .asFlow()
                    .collect { fetchHistoryChange ->
                        onFetchHistoryChanged(fetchHistoryChange)
                    }
        }

        scope.launch {
            delay(250L)
            withView { rebuildFetchResultsList(fetchResultList) }
        }
    }

    private suspend fun onFetchHistoryChanged(fetchHistoryChange: ArchivesManager.FetchHistoryChange) {
        when (fetchHistoryChange.changeType) {
            ArchivesManager.FetchHistoryChangeType.Insert -> {
                val alreadyContains = mutex.withLock {
                    return@withLock fetchResultList.firstOrNull { fetchResult ->
                        fetchResult.databaseId == fetchHistoryChange.databaseId
                    } != null
                }

                if (alreadyContains) {
                    // Already added
                    return
                }

                val latestHistory = archivesManager.selectLatestFetchHistory(
                        fetchHistoryChange.archiveDescriptor
                ).safeUnwrap { error ->
                    Logger.e(TAG, "Error while trying to get latest fetch history " +
                            "for archive (${fetchHistoryChange.archiveDescriptor})", error)

                    withView { showUnknownErrorToast() }
                    return
                }

                mutex.withLock {
                    // Reload fetch history
                    fetchResultList.clear()
                    fetchResultList.addAll(latestHistory)
                }

                rebuildFetchResults()
            }
            ArchivesManager.FetchHistoryChangeType.Delete -> {
                val index = mutex.withLock {
                    return@withLock fetchResultList.indexOfFirst { fetchResult ->
                        fetchResult.databaseId == fetchHistoryChange.databaseId
                    }
                }

                if (index < 0) {
                    // Already deleted
                    return
                }

                mutex.withLock {
                    // Remove fetch result
                    fetchResultList.removeAt(index)
                }

                rebuildFetchResults()
            }
        }.exhaustive
    }

    fun deleteFetchResult(fetchResult: ThirdPartyArchiveFetchResult) {
        scope.launch {
            archivesManager.deleteFetchResult(fetchResult).safeUnwrap { error ->
                Logger.e(TAG, "Failed to delete fetch result ($fetchResult) from the DB", error)

                withView { showDeleteErrorToast() }
                return@launch
            }

            mutex.withLock {
                val index = fetchResultList.indexOfFirst { fetchRes ->
                    fetchRes.databaseId == fetchResult.databaseId
                }

                if (index < 0) {
                    return@launch
                }

                fetchResultList.removeAt(index)
            }

            rebuildFetchResults()
        }
    }

    private suspend fun rebuildFetchResults() {
        withView {
            onFetchResultListChanged()
            rebuildFetchResultsList(fetchResultList)

            if (fetchResultList.isEmpty()) {
                popController()
            }
        }
    }

    companion object {
        private const val TAG = "ArchiveFetchHistoryPresenter"
    }
}