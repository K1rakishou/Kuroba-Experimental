package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.ui.compose.reorder.move
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterWatchGroup
import com.github.k1rakishou.model.repository.ChanFilterRepository
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import dagger.Lazy
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
  private val _chanFilterRepository: Lazy<ChanFilterRepository>,
  private val _chanPostRepository: Lazy<ChanPostRepository>,
  private val _chanFilterWatchRepository: Lazy<ChanFilterWatchRepository>,
  private val _postFilterHighlightManager: Lazy<PostFilterHighlightManager>,
  private val _postFilterManager: Lazy<PostFilterManager>
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

  private val chanFilterRepository: ChanFilterRepository
    get() = _chanFilterRepository.get()
  private val chanPostRepository: ChanPostRepository
    get() = _chanPostRepository.get()
  private val chanFilterWatchRepository: ChanFilterWatchRepository
    get() = _chanFilterWatchRepository.get()
  private val postFilterManager: PostFilterManager
    get() = _postFilterManager.get()
  private val postFilterHighlightManager: PostFilterHighlightManager
    get() = _postFilterHighlightManager.get()

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

  fun allFiltersEnabled(): Boolean {
    return lock.read {
      if (filters.isEmpty()) {
        return@read true
      }

      return@read filters.all { chanFilter -> chanFilter.enabled }
    }
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

    filterChangesFlow.emit(FilterEvent.Initialized)
  }

  fun createOrUpdateFilter(chanFilter: ChanFilter, onFinished: () -> Unit) {
    createOrUpdateFilters(listOf(chanFilter), onFinished)
  }

  fun createOrUpdateFilters(chanFilters: Collection<ChanFilter>, onFinished: () -> Unit) {
    if (chanFilters.isEmpty()) {
      onFinished()
      return
    }

    serializedCoroutineExecutor.post {
      val toCreate = mutableMapOf<ChanFilter, Int>()
      val toUpdate = mutableMapOf<ChanFilter, Int>()

      lock.read {
        for (chanFilter in chanFilters) {
          if (!chanFilter.hasDatabaseId()) {
            toCreate[chanFilter] = -1
            continue
          }

          val prevIndex = filters
            .indexOfFirst { filter -> filter.getDatabaseId() == chanFilter.getDatabaseId() }

          if (prevIndex < 0) {
            toCreate[chanFilter] = prevIndex
          } else {
            toUpdate[chanFilter] = prevIndex
          }
        }
      }

      var anySucceeded = false

      if (toCreate.isNotEmpty()) {
        val createdFilters = createNewFilterInternal(toCreate)
        if (createdFilters.isNotEmpty()) {
          anySucceeded = true

          filterChangesFlow.emit(FilterEvent.Created(createdFilters))
        }
      }

      if (toUpdate.isNotEmpty()) {
        val updatedFilters = updateOldFilterInternal(toUpdate)
        if (updatedFilters.isNotEmpty()) {
          anySucceeded = true
          filterChangesFlow.emit(FilterEvent.Updated(updatedFilters))
        }
      }

      if (anySucceeded) {
        clearFiltersAndPostHashes()
        clearFilterWatchGroups(chanFilters)
      }

      onFinished()
    }
  }

  fun onFilterMoved(fromIndex: Int, toIndex: Int, onMoved: (Boolean) -> Unit) {
    serializedCoroutineExecutor.post {
      val moved = lock.write {
        if (filters.size < 2) {
          return@write false
        }

        return@write filters.move(fromIdx = fromIndex, toIdx = toIndex)
      }

      onMoved(moved)
    }
  }

  suspend fun persistReorderedFilters() {
    val allFilters = lock.write { filters.toList() }

    chanFilterRepository.updateAllFilters(allFilters)
      .peekError { error -> Logger.e(TAG, "Failed to update filters in database", error) }
      .ignore()
  }

  fun deleteFilter(chanFilter: ChanFilter, onDeleted: () -> Unit) {
    deleteFilters(listOf(chanFilter), onDeleted)
  }

  fun deleteFilters(chanFilters: Collection<ChanFilter>, onDeleted: () -> Unit) {
    if (chanFilters.isEmpty()) {
      onDeleted()
      return
    }

    serializedCoroutineExecutor.post {
      var atLeastOneDeleted = false
      val filterWatchGroupResultMap = mutableMapOf<Long, ModularResult<List<ChanFilterWatchGroup>>>()

      for (chanFilter in chanFilters) {
        val deletedFilterIndex = lock.write {
          val index = filters.indexOf(chanFilter)
          if (index >= 0) {
            filters.remove(chanFilter)
          }

          return@write index
        }

        if (deletedFilterIndex < 0) {
          continue
        }

        // It is important to do this before actually deleting the filter, otherwise this will always
        // be returning null.
        val filterWatchGroupResult = chanFilterWatchRepository.getFilterWatchGroupsByFilterId(
          chanFilter.getDatabaseId()
        )

        filterWatchGroupResultMap[chanFilter.getDatabaseId()] = filterWatchGroupResult

        val success = chanFilterRepository.deleteFilter(chanFilter)
          .peekError { error -> Logger.e(TAG, "chanFilterRepository.deleteFilter() error", error) }
          .mapErrorToValue { false }

        if (!success) {
          lock.write { filters.add(chanFilter) }
          continue
        }

        atLeastOneDeleted = true
      }

      if (!atLeastOneDeleted) {
        onDeleted()
        return@post
      }

      val allFilters = lock.read { filters.map { filter -> filter.copy() } }

      chanFilterRepository.updateAllFilters(allFilters)
        .peekError { error -> Logger.e(TAG, "Failed to update filters in database", error) }
        .ignore()

      clearFiltersAndPostHashes()
      clearFilterWatchGroups(chanFilters)

      filterChangesFlow.emit(FilterEvent.Deleted(chanFilters))

      filterWatchGroupResultMap.entries.forEach { (databaseId, filterWatchGroupResult) ->
        if (filterWatchGroupResult is ModularResult.Error) {
          Logger.e(TAG, "Failed to get filter watch group by filter id", filterWatchGroupResult.error)
        } else {
          val filterWatchGroups = (filterWatchGroupResult as ModularResult.Value).value
          if (filterWatchGroups.isNotEmpty()) {
            val chanFilter = chanFilters
              .firstOrNull { chanFilter -> chanFilter.getDatabaseId() == databaseId }

            if (chanFilter != null) {
              filterGroupDeletionsFlow.tryEmit(FilterDeletionEvent(chanFilter, filterWatchGroups))
            }
          }
        }
      }

      onDeleted()
    }
  }

  fun deleteAllFilters(onFinished: (Throwable?) -> Unit) {
    serializedCoroutineExecutor.post {
      val result = chanFilterRepository.deleteAll()
      if (result is ModularResult.Error) {
        onFinished(result.error)
        return@post
      }

      clearFiltersAndPostHashes()

      val allFilters = lock.read { filters.map { filter -> filter.copy() } }
      allFilters.forEach { chanFilter -> clearFilterWatchGroups(chanFilter) }

      lock.read { filters.clear() }
      filterChangesFlow.emit(FilterEvent.Deleted(allFilters))

      onFinished(null)
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

      filterChangesFlow.emit(FilterEvent.Updated(allFilters))
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

  fun getEnabledHighlightFilters(): List<ChanFilter> {
    return lock.read {
      return@read filters
        .filter { chanFilter -> chanFilter.isEnabledHighlightFilter() }
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

  // Whenever we update the filters we need to reload already cached posts and parse them again.
  // We also need to clear the existing post highlights so that they can get recalculated
  private fun clearFiltersAndPostHashes() {
    postFilterManager.clear()
    chanPostRepository.clearPostHashes()
    postFilterHighlightManager.clear()
  }

  // Whenever we create/update/or delete a filter with WATCH flag, we want to delete all filter
  // watch groups from the DB. The groups will be created anew on the next filter watch update cycle.
  // We do not do this when enabling/disabling filters.
  private suspend fun clearFilterWatchGroups(chanFilter: ChanFilter) {
    clearFilterWatchGroups(listOf(chanFilter))
  }

  private suspend fun clearFilterWatchGroups(chanFilters: Collection<ChanFilter>) {
    if (chanFilters.none { chanFilter -> chanFilter.isWatchFilter() }) {
      return
    }

    chanFilterWatchRepository.clearFilterWatchGroups()
      .peekError { error -> Logger.e(TAG, "clearFilterWatchGroups() error", error) }
      .ignore()
  }

  private suspend fun createNewFilterInternal(filtersToCreate: Map<ChanFilter, Int>): List<ChanFilter> {
    val createdFilters = mutableListOf<ChanFilter>()

    for ((chanFilter, indexOfThisFilter) in filtersToCreate.entries) {
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

        continue
      }

      val success = lock.write {
        val newIndexOfThisFilter = filters
          .indexOfFirst { filter -> !filter.hasDatabaseId() && filter == chanFilter }

        if (newIndexOfThisFilter < 0) {
          Logger.e(TAG, "Failed to update filter databaseId, it was already removed from the filters cache")
          return@write false
        }

        filters.getOrNull(newIndexOfThisFilter)?.setDatabaseId(databaseId)
        return@write true
      }

      if (success) {
        createdFilters += chanFilter
      }
    }

    return createdFilters
  }

  private suspend fun updateOldFilterInternal(filtersToUpdate: Map<ChanFilter, Int>): List<ChanFilter> {
    val updatedFilters = mutableListOf<ChanFilter>()

    for ((chanFilter, indexOfThisFilter) in filtersToUpdate.entries) {
      val updated = lock.write {
        val prevChanFilter = filters.getOrNull(indexOfThisFilter)
        if (prevChanFilter == null) {
          Logger.e(TAG, "Failed to update filter, it was already removed from the filters cache")
          return@write false
        }

        if (prevChanFilter == chanFilter) {
          return@write false
        }

        require(prevChanFilter.hasDatabaseId()) { "prevFilter has no database id!" }
        filters[indexOfThisFilter] = mergePrevAndNewFilters(prevChanFilter, chanFilter)

        return@write true
      }

      if (!updated) {
        continue
      }

      updatedFilters += chanFilter
    }

    if (updatedFilters.isEmpty()) {
      return emptyList()
    }

    val allFilters = lock.read { filters.map { filter -> filter.copy() } }

    val updatedInDatabase = chanFilterRepository.updateAllFilters(allFilters)
      .peekError { error -> Logger.e(TAG, "Failed to update filter in database", error) }
      .mapErrorToValue { false }

    if (!updatedInDatabase) {
      return emptyList()
    }

    return updatedFilters
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
      note = newChanFilter.note,
      applyToReplies = newChanFilter.applyToReplies,
      onlyOnOP = newChanFilter.onlyOnOP,
      applyToSaved = newChanFilter.applyToSaved,
      applyToEmptyComments = newChanFilter.applyToEmptyComments,
    )
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

    class Created(val chanFilters: Collection<ChanFilter>) : FilterEvent() {
      override fun hasWatchFilter(): Boolean = chanFilters.any { chanFilter -> chanFilter.isWatchFilter() }
    }

    class Updated(val chanFilters: Collection<ChanFilter>) : FilterEvent() {
      override fun hasWatchFilter(): Boolean {
        return chanFilters.any { chanFilter -> chanFilter.isWatchFilter() }
      }
    }

    class Deleted(val chanFilters: Collection<ChanFilter>) : FilterEvent() {
      override fun hasWatchFilter(): Boolean {
        return chanFilters.any { chanFilter -> chanFilter.isWatchFilter() }
      }
    }
  }

  companion object {
    private const val TAG = "ChanFilterManager"
  }
}