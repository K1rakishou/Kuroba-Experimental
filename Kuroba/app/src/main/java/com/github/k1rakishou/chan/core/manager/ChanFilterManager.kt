package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterWatchGroup
import com.github.k1rakishou.model.repository.ChanFilterRepository
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanFilterManager(
  private val isDevBuild: Boolean,
  private val appScope: CoroutineScope,
  private val chanFilterRepository: ChanFilterRepository,
  private val chanPostRepository: ChanPostRepository,
  private val chanFilterWatchRepository: ChanFilterWatchRepository,
  private val postFilterManager: PostFilterManager
) {
  private val filterChangesFlow = MutableSharedFlow<FilterEvent>(
    extraBufferCapacity = 32,
    onBufferOverflow = BufferOverflow.SUSPEND
  )

  private val filterGroupDeletionsFlow = MutableSharedFlow<FilterDeletionEvent>(
    extraBufferCapacity = 32,
    onBufferOverflow = BufferOverflow.SUSPEND
  )

  private val suspendableInitializer = SuspendableInitializer<Unit>("ChanFilterManager")
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val filters = mutableListWithCap<ChanFilter>(32)

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    Logger.d(TAG, "ChanFilterManager.initialize()")
    serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

    appScope.launch(Dispatchers.IO) {
      Logger.d(TAG, "loadFiltersInternal() start")
      val time = measureTime { loadFiltersInternal() }
      Logger.d(TAG, "loadFiltersInternal() end, took ${time}")
    }
  }

  fun listenForFiltersChanges(): SharedFlow<FilterEvent> {
    return filterChangesFlow
      .asSharedFlow()
  }

  fun listenForFilterGroupDeletions(): SharedFlow<FilterDeletionEvent> {
    return filterGroupDeletionsFlow
      .asSharedFlow()
  }

  private suspend fun loadFiltersInternal() {
    val loadFiltersResult = chanFilterRepository.loadAllFilters()
    if (loadFiltersResult is ModularResult.Error) {
      Logger.e(TAG, "loadFiltersInternal() chanFilterRepository.loadAllFilters() error", loadFiltersResult.error)
      suspendableInitializer.initWithError(loadFiltersResult.error)
      return
    }

    try {
      loadFiltersResult as ModularResult.Value

      lock.write {
        loadFiltersResult.value.forEach { chanFilter -> require(chanFilter.hasDatabaseId()) }

        filters.clear()
        filters.addAll(loadFiltersResult.value)
      }

      suspendableInitializer.initWithValue(Unit)
      Logger.d(TAG, "loadFiltersInternal() done. Loaded ${loadFiltersResult.value.size} filters")
    } catch (error: Throwable) {
      suspendableInitializer.initWithError(error)
      Logger.e(TAG, "loadFiltersInternal() unknown error", error)
    }

    filterChangesFlow.tryEmit(FilterEvent.Initialized)
  }

  fun createOrUpdateFilter(chanFilter: ChanFilter, onUpdated: () -> Unit) {
    serializedCoroutineExecutor.post {
      val indexOfThisFilter = lock.read {
        if (!chanFilter.hasDatabaseId()) {
          return@read -1
        }

        return@read filters.indexOfFirst { filter -> filter.getDatabaseId() == chanFilter.getDatabaseId() }
      }

      val createFilter = indexOfThisFilter < 0

      val success = if (createFilter) {
        createNewFilterInternal(indexOfThisFilter, chanFilter)
      } else {
        updateOldFilterInternal(indexOfThisFilter, chanFilter)
      }

      if (success) {
        clearFiltersAndPostHashes()
        clearFilterWatchGroups(chanFilter)
      }

      val filterEvent = if (createFilter) {
        FilterEvent.Created(chanFilter)
      } else {
        FilterEvent.Updated(listOf(chanFilter))
      }

      filterChangesFlow.tryEmit(filterEvent)
      onUpdated()
    }
  }

  fun onFilterMoved(from: Int, to: Int, onMoved: () -> Unit) {
    serializedCoroutineExecutor.post {
      val allFilters = lock.write {
        filters.add(to, filters.removeAt(from))
        filters.map { filter -> filter.copy() }
      }

      chanFilterRepository.updateAllFilters(allFilters)
        .peekError { error -> Logger.e(TAG, "Failed to update filters in database", error) }
        .ignore()

      onMoved()
    }
  }

  fun deleteFilter(chanFilter: ChanFilter, onUpdated: () -> Unit) {
    serializedCoroutineExecutor.post {
      val deletedFilterIndex = lock.write {
        val index = filters.indexOf(chanFilter)
        if (index >= 0) {
          filters.remove(chanFilter)
        }

        return@write index
      }

      if (deletedFilterIndex < 0) {
        onUpdated()
        return@post
      }

      // It is important to do this before actually deleting the filter, otherwise this will always
      // be returning null.
      val filterWatchGroupResult = chanFilterWatchRepository.getFilterWatchGroupsByFilterId(
        chanFilter.getDatabaseId()
      )

      val success = chanFilterRepository.deleteFilter(chanFilter)
        .peekError { error -> Logger.e(TAG, "chanFilterRepository.deleteFilter() error", error) }
        .mapErrorToValue { false }

      if (!success) {
        lock.write { filters.add(chanFilter) }
        onUpdated()
        return@post
      }

      val allFilters = lock.read { filters.map { filter -> filter.copy() } }

      chanFilterRepository.updateAllFilters(allFilters)
        .peekError { error -> Logger.e(TAG, "Failed to update filters in database", error) }
        .ignore()

      clearFiltersAndPostHashes()
      clearFilterWatchGroups(chanFilter)

      filterChangesFlow.tryEmit(FilterEvent.Deleted(listOf(chanFilter)))

      if (filterWatchGroupResult is ModularResult.Error) {
        Logger.e(TAG, "Failed to get filter watch group by filter id", filterWatchGroupResult.error)
      } else {
        val filterWatchGroups = (filterWatchGroupResult as ModularResult.Value).value
        if (filterWatchGroups.isNotEmpty()) {
          filterGroupDeletionsFlow.tryEmit(FilterDeletionEvent(chanFilter, filterWatchGroups))
        }
      }

      onUpdated()
    }
  }

  fun enableDisableFilters(filters: List<ChanFilter>, enable: Boolean, onUpdated: () -> Unit) {
    serializedCoroutineExecutor.post {
      if (filters.isEmpty()) {
        onUpdated()
        return@post
      }

      val changed = lock.write {
        val filterIds = filters.map { chanFilter ->
          val databaseId = chanFilter.getDatabaseId()
          require(databaseId > 0L) { "Bad databaseId: ${databaseId}, filter = ${chanFilter}" }

          return@map databaseId
        }

        var changed = false

        for ((index, chanFilter) in this.filters.withIndex()) {
          if (chanFilter.getDatabaseId() in filterIds && chanFilter.enabled != enable) {
            this.filters[index] = chanFilter.copy(enable = enable)
            changed = true
          }
        }

        return@write changed
      }

      if (!changed) {
        onUpdated()
        return@post
      }

      val allFilters = lock.read { this.filters.map { filter -> filter.copy() } }

      chanFilterRepository.updateAllFilters(allFilters)
        .peekError { error -> Logger.e(TAG, "Failed to update filters in database", error) }
        .ignore()

      clearFiltersAndPostHashes()

      filterChangesFlow.tryEmit(FilterEvent.Updated(allFilters))
      onUpdated()
    }
  }

  fun getAllFilters(): List<ChanFilter> {
    return lock.read { filters.map { chanFilter -> chanFilter.copy() } }
  }

  fun viewAllFilters(viewer: (ChanFilter) -> Unit) {
    lock.read {
      filters.forEach { chanFilter ->
        viewer(chanFilter)
      }
    }
  }

  fun viewAllFiltersWhile(viewer: (ChanFilter) -> Boolean) {
    lock.read {
      for (chanFilter in filters) {
        if (!viewer(chanFilter)) {
          return@read
        }
      }
    }
  }

  fun getEnabledWatchFilters(): List<ChanFilter> {
    return lock.read {
      return@read filters
        .filter { chanFilter -> chanFilter.isEnabledWatchFilter() }
    }
  }

  fun hasEnabledWatchFilters(): Boolean {
    return lock.read {
      return@read filters.any { chanFilter ->
        return@any chanFilter.isEnabledWatchFilter()
      }
    }
  }

  fun filtersCount(): Int {
    return lock.read { filters.size }
  }

  fun getEnabledFiltersSorted(): List<ChanFilter> {
    return lock.read {
      return@read filters.filter { filter -> filter.enabled }
        .map { filter -> filter.copy() }
    }
  }

  // Whenever we update the filters we need to reload already cached posts and parse them again
  private fun clearFiltersAndPostHashes() {
    postFilterManager.clear()
    chanPostRepository.clearPostHashes()
  }

  // Whenever we create/update/or delete a filter with WATCH flag, we want to delete all filter
  // watch groups from the DB. The groups will be created anew on the next filter watch update cycle.
  // We do not do this when enabling/disabling filters.
  private suspend fun clearFilterWatchGroups(chanFilter: ChanFilter) {
    if (!chanFilter.isWatchFilter()) {
      return
    }

    chanFilterWatchRepository.clearFilterWatchGroups()
      .peekError { error -> Logger.e(TAG, "clearFilterWatchGroups() error", error) }
      .ignore()
  }

  private suspend fun updateOldFilterInternal(indexOfThisFilter: Int, newChanFilter: ChanFilter): Boolean {
    val updated = lock.write {
      val prevChanFilter = filters.getOrNull(indexOfThisFilter)
      if (prevChanFilter == null) {
        Logger.e(TAG, "Failed to update filter, it was already removed from the filters cache")
        return@write false
      }

      require(prevChanFilter.hasDatabaseId()) { "prevFilter has no database id!" }
      filters[indexOfThisFilter] = mergePrevAndNewFilters(prevChanFilter, newChanFilter)

      return@write true
    }

    if (!updated) {
      return false
    }

    val allFilters = lock.read { filters.map { filter -> filter.copy() } }

    return chanFilterRepository.updateAllFilters(allFilters)
      .peekError { error -> Logger.e(TAG, "Failed to update filter in database", error) }
      .mapErrorToValue { false }
  }

  private fun mergePrevAndNewFilters(prevChanFilter: ChanFilter, newChanFilter: ChanFilter): ChanFilter {
    val databaseId = max(prevChanFilter.getDatabaseId(), newChanFilter.getDatabaseId())

    return ChanFilter(
      filterDatabaseId = databaseId,
      enabled = newChanFilter.enabled,
      type = newChanFilter.type,
      pattern = newChanFilter.pattern,
      boards = newChanFilter.boards,
      action = newChanFilter.action,
      color = newChanFilter.color,
      applyToReplies = newChanFilter.applyToReplies,
      onlyOnOP = newChanFilter.onlyOnOP,
      applyToSaved = newChanFilter.applyToSaved
    )
  }

  private suspend fun createNewFilterInternal(indexOfThisFilter: Int, chanFilter: ChanFilter): Boolean {
    val index = if (indexOfThisFilter < 0) {
      lock.write {
        filters.add(chanFilter)
        filters.lastIndex
      }
    } else {
      indexOfThisFilter
    }

    val databaseId = chanFilterRepository.createFilter(chanFilter, index)
      .peekError { error -> Logger.e(TAG, "chanFilterRepository.createFilter() error", error) }
      .mapErrorToValue { -1L }

    if (databaseId <= 0L) {
      Logger.e(TAG, "Failed to create filter ${chanFilter}, bad databaseId = $databaseId")
      lock.write { filters.remove(chanFilter) }

      return false
    }

    return lock.write {
      val newIndexOfThisFilter = filters.indexOfFirst { filter -> filter == chanFilter }
      if (newIndexOfThisFilter < 0) {
        Logger.e(TAG, "Failed to update filter databaseId, it was already removed from the filters cache")
        return@write false
      }

      filters.getOrNull(newIndexOfThisFilter)?.setDatabaseId(databaseId)
      return@write true
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "ChanFilterManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "ChanFilterManager initialization completed, took $duration")
  }

  fun isReady() = suspendableInitializer.isInitialized()

  data class FilterDeletionEvent(
    val chanFilter: ChanFilter,
    val filterWatchGroups: List<ChanFilterWatchGroup>
  )

  sealed class FilterEvent {
    abstract fun hasWatchFilter(): Boolean

    object Initialized : FilterEvent() {
      override fun hasWatchFilter(): Boolean = false
    }

    class Created(val chanFilter: ChanFilter) : FilterEvent() {
      override fun hasWatchFilter(): Boolean = chanFilter.isWatchFilter()
    }

    class Updated(val chanFilters: List<ChanFilter>) : FilterEvent() {
      override fun hasWatchFilter(): Boolean {
        return chanFilters.any { chanFilter -> chanFilter.isWatchFilter() }
      }
    }

    class Deleted(val chanFilters: List<ChanFilter>) : FilterEvent() {
      override fun hasWatchFilter(): Boolean {
        return chanFilters.any { chanFilter -> chanFilter.isWatchFilter() }
      }
    }
  }

  companion object {
    private const val TAG = "ChanFilterManager"
  }
}