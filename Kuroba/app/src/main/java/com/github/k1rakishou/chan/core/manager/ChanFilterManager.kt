package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.repository.ChanFilterRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
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
  private val postFilterManager: PostFilterManager
) {
  private val filtersChangedChannel = MutableSharedFlow<Unit>(
    replay = 1,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val suspendableInitializer = SuspendableInitializer<Unit>("ChanFilterManager")
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val filters = mutableListWithCap<ChanFilter>(128)

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    Logger.d(TAG, "ChanFilterManager.initialize()")
    serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

    appScope.launch(Dispatchers.Default) {
      val time = measureTime { loadFiltersInternal() }
      Logger.d(TAG, "loadFilters() took ${time}")
    }
  }

  fun listenForFiltersChanges(): Flow<Unit> {
    return filtersChangedChannel
      .asSharedFlow()
      .catch { error -> Logger.e(TAG, "Error while listening for filtersChangedSubject updates", error) }
  }

  private suspend fun loadFiltersInternal() {
    val loadFiltersResult = chanFilterRepository.loadAllFilters()
    if (loadFiltersResult is ModularResult.Error) {
      Logger.e(TAG, "chanFilterRepository.loadAllFilters() error", loadFiltersResult.error)
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
    } catch (error: Throwable) {
      Logger.e(TAG, "ChanFilterManager initialization error", error)
      suspendableInitializer.initWithError(error)
    }

    filtersChangedChannel.tryEmit(Unit)
  }

  fun createOrUpdateFilter(chanFilter: ChanFilter, onUpdated: () -> Unit) {
    serializedCoroutineExecutor.post {
      val indexOfThisFilter = lock.read {
        if (!chanFilter.hasDatabaseId()) {
          return@read -1
        }

        return@read filters.indexOfFirst { filter -> filter.getDatabaseId() == chanFilter.getDatabaseId() }
      }

      val success = if (indexOfThisFilter < 0) {
        createNewFilterInternal(indexOfThisFilter, chanFilter)
      } else {
        updateOldFilterInternal(indexOfThisFilter, chanFilter)
      }

      if (success) {
        clearFiltersAndPostHashes()
      }

      filtersChangedChannel.tryEmit(Unit)
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

      filtersChangedChannel.tryEmit(Unit)
      onUpdated()
    }
  }

  fun enableDisableFilters(filters: List<ChanFilter>, enable: Boolean, onUpdated: () -> Unit) {
    serializedCoroutineExecutor.post {
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

      filtersChangedChannel.tryEmit(Unit)
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

  fun getEnabledWatchFilters(): List<ChanFilter> {
    return lock.read {
      return@read filters.filter { chanFilter -> chanFilter.isEnabledWatchFilter() }
        .map { chanFilter -> chanFilter.copy() }
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

  companion object {
    private const val TAG = "ChanFilterManager"
  }
}