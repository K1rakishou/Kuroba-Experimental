package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.helper.OneShotRunnable
import com.github.k1rakishou.chan.ui.compose.reorder.move
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.CompositeCatalogRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

class CompositeCatalogManager(
  private val compositeCatalogRepository: CompositeCatalogRepository,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
) {
  private val oneShotRunnable = OneShotRunnable()
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val compositeCatalogs = mutableListOf<CompositeCatalog>()
  private val orderCounter = AtomicInteger(0)

  private val _compositeCatalogUpdatesFlow = MutableSharedFlow<Event>(extraBufferCapacity = 16)
  val compositeCatalogUpdateEventsFlow: SharedFlow<Event>
    get() = _compositeCatalogUpdatesFlow.asSharedFlow()

  suspend fun doWithLockedCompositeCatalogs(func: suspend (List<CompositeCatalog>) -> Unit) {
    ensureInitialized()

    mutex.withLock { func(compositeCatalogs) }
  }

  suspend fun byCompositeCatalogDescriptor(descriptor: ChanDescriptor.CompositeCatalogDescriptor): CompositeCatalog? {
    ensureInitialized()

    return mutex.withLock {
      return@withLock compositeCatalogs
        .firstOrNull { compositeCatalog -> compositeCatalog.compositeCatalogDescriptor.asSet == descriptor.asSet }
    }
  }

  suspend fun create(compositeCatalog: CompositeCatalog): ModularResult<Unit> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLock {
        compositeCatalogRepository.create(
          compositeCatalog = compositeCatalog,
          order = getOrder(compositeCatalog.compositeCatalogDescriptor)
        )
          .onError { error -> Logger.e(TAG, "create($compositeCatalog) error", error) }
          .unwrap()

        compositeCatalogs.add(compositeCatalog)

        val event = Event.Created(compositeCatalog.compositeCatalogDescriptor)
        updateCurrentCatalogDescriptorIfNeeded(event)
        _compositeCatalogUpdatesFlow.emit(event)
      }
    }
  }

  suspend fun update(
    compositeCatalog: CompositeCatalog,
    prevCompositeCatalog: CompositeCatalog
  ): ModularResult<Unit> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLock {
        val prevIndex = compositeCatalogs
          .indexOfFirst { catalog -> catalog.compositeCatalogDescriptor == prevCompositeCatalog.compositeCatalogDescriptor }

        if (prevIndex < 0) {
          return@withLock
        }

        compositeCatalogRepository.delete(prevCompositeCatalog)
          .onError { error -> Logger.e(TAG, "delete($prevCompositeCatalog) error", error) }
          .unwrap()

        compositeCatalogRepository.create(
          compositeCatalog = compositeCatalog,
          order = prevIndex
        )
          .onError { error -> Logger.e(TAG, "create($compositeCatalog) error", error) }
          .unwrap()

        compositeCatalogs[prevIndex] = compositeCatalog

        val event = Event.Updated(
          prevCatalogDescriptor = prevCompositeCatalog.compositeCatalogDescriptor,
          newCatalogDescriptor = compositeCatalog.compositeCatalogDescriptor
        )

        _compositeCatalogUpdatesFlow.emit(event)
      }
    }
  }

  suspend fun move(fromIndex: Int, toIndex: Int): ModularResult<Unit> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLock {
        compositeCatalogs.getOrNull(fromIndex) ?: return@Try
        compositeCatalogs.getOrNull(toIndex) ?: return@Try
        compositeCatalogs.move(fromIndex, toIndex)
      }
    }
  }

  suspend fun viewCatalogsOrdered(viewFunc: (CompositeCatalog) -> Unit) {
    ensureInitialized()

    mutex.withLock {
      compositeCatalogs.forEach(viewFunc)
    }
  }

  suspend fun count(): Int {
    ensureInitialized()

    return mutex.withLock { compositeCatalogs.size }
  }

  suspend fun firstCompositeCatalog(): CompositeCatalog? {
    ensureInitialized()

    return mutex.withLock { compositeCatalogs.firstOrNull() }
  }

  suspend fun orderOf(compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor?): Int? {
    if (compositeCatalogDescriptor == null) {
      return null
    }

    ensureInitialized()

    return mutex.withLock {
      val index = compositeCatalogs.indexOfFirst { compositeCatalog ->
        compositeCatalog.compositeCatalogDescriptor == compositeCatalogDescriptor
      }

      if (index < 0) {
        return@withLock null
      }

      return@withLock index
    }
  }

  suspend fun delete(compositeCatalog: CompositeCatalog): ModularResult<Unit> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLock {
        val compositeCatalogIndex = compositeCatalogs.indexOfFirst { catalog ->
          catalog.compositeCatalogDescriptor == compositeCatalog.compositeCatalogDescriptor
        }

        if (compositeCatalogIndex < 0) {
          return@withLock
        }

        compositeCatalogRepository.delete(compositeCatalog)
          .onError { error -> Logger.e(TAG, "delete($compositeCatalog) error", error) }
          .unwrap()

        val removed = compositeCatalogs.removeIfKt { catalog ->
          catalog.compositeCatalogDescriptor == compositeCatalog.compositeCatalogDescriptor
        }

        if (removed) {
          val event = Event.Deleted(compositeCatalog.compositeCatalogDescriptor)
          updateCurrentCatalogDescriptorIfNeeded(event)
          _compositeCatalogUpdatesFlow.emit(event)
        }
      }
    }
  }

  suspend fun persistAll(): ModularResult<Unit> {
    if (!oneShotRunnable.alreadyRun) {
      return ModularResult.value(Unit)
    }

    return mutex.withLock { compositeCatalogRepository.persist(compositeCatalogs) }
  }

  private fun getOrder(
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor
  ): Int {
    require(mutex.isLocked) { "mutex Must be locked!" }

    if (compositeCatalogs.isEmpty()) {
      return orderCounter.get()
    }

    val existingIndex = compositeCatalogs.indexOfFirst { compositeCatalog ->
      compositeCatalog.compositeCatalogDescriptor == compositeCatalogDescriptor
    }

    if (existingIndex >= 0) {
      return existingIndex
    }

    return orderCounter.incrementAndGet()
  }

  private suspend fun ensureInitialized() {
    oneShotRunnable.runIfNotYet {
      val loadedCompositeCatalogs = compositeCatalogRepository.loadAll()
        .unwrap()
      val maxOrder = compositeCatalogRepository.maxOrder()
        .unwrap()

      mutex.withLock {
        compositeCatalogs.clear()
        compositeCatalogs.addAll(loadedCompositeCatalogs)

        orderCounter.set(maxOrder)
      }
    }
  }

  private fun updateCurrentCatalogDescriptorIfNeeded(event: Event) {
    require(mutex.isLocked) { "Mutex must be locked!" }

    val currentCatalogDescriptor = currentOpenedDescriptorStateManager.currentCatalogDescriptor
    when (event) {
      is Event.Updated -> return
      is Event.Created -> {
        if (currentCatalogDescriptor != null) {
          return
        }

        val compositeCatalogDescriptor = compositeCatalogs.firstOrNull()?.compositeCatalogDescriptor
        if (compositeCatalogDescriptor == null) {
          return
        }

        currentOpenedDescriptorStateManager.updateCatalogDescriptor(compositeCatalogDescriptor)
      }
      is Event.Deleted -> {
        if (currentCatalogDescriptor == null) {
          return
        }

        if (currentCatalogDescriptor !is ChanDescriptor.CompositeCatalogDescriptor) {
          return
        }

        if (event.compositeCatalogDescriptor != currentCatalogDescriptor) {
          return
        }

        currentOpenedDescriptorStateManager.updateCatalogDescriptor(null)
      }
    }
  }

  sealed class Event {
    data class Created(val compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor) : Event()
    data class Updated(
      val prevCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor,
      val newCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor,
    ) : Event()
    data class Deleted(val compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor) : Event()
  }

  companion object {
    private const val TAG = "CompositeCatalogManager"
  }
}