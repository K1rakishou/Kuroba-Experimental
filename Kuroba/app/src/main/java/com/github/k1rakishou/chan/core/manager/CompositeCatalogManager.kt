package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.helper.OneShotRunnable
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
    mutex.withLock { func(compositeCatalogs) }
  }

  suspend fun byCompositeCatalogDescriptor(descriptor: ChanDescriptor.CompositeCatalogDescriptor): CompositeCatalog? {
    return mutex.withLock {
      return@withLock compositeCatalogs
        .firstOrNull { compositeCatalog -> compositeCatalog.compositeCatalogDescriptor == descriptor }
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
          .peekError { error -> Logger.e(TAG, "createOrUpdate($compositeCatalog) error", error) }
          .unwrap()

        val prevIndex = compositeCatalogs.indexOfFirst { catalog ->
          catalog.compositeCatalogDescriptor == compositeCatalog.compositeCatalogDescriptor
        }

        if (prevIndex >= 0) {
          compositeCatalogs[prevIndex] = compositeCatalog
          _compositeCatalogUpdatesFlow.emit(Event.Updated(compositeCatalog.compositeCatalogDescriptor))
        } else {
          compositeCatalogs.add(compositeCatalog)

          val event = Event.Created(compositeCatalog.compositeCatalogDescriptor)
          updateCurrentCatalogDescriptorIfNeeded(event)
          _compositeCatalogUpdatesFlow.emit(event)
        }
      }
    }
  }

  suspend fun move(movedCompositeCatalog: CompositeCatalog, movingUp: Boolean): ModularResult<Unit> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLock {
        val fromIndex = if (movingUp) {
          val index = compositeCatalogs.indexOfFirst { catalog -> catalog == movedCompositeCatalog }
          if (index < 1) {
            // Can't move if the index is not at least 1
            return@withLock
          }

          index
        } else {
          val index = compositeCatalogs.indexOfFirst { catalog -> catalog == movedCompositeCatalog }
          if (index >= compositeCatalogs.lastIndex) {
            // Can't move if the index is not at least (lastIndex - 1)
            return@withLock
          }

          index
        }

        val toIndex = if (movingUp) {
          fromIndex - 1
        } else {
          fromIndex + 1
        }

        compositeCatalogRepository.move(
          fromCompositeCatalog = movedCompositeCatalog,
          toCompositeCatalog = compositeCatalogs[toIndex]
        )
          .peekError { error ->
            Logger.e(TAG, "move(movedCompositeCatalog=$movedCompositeCatalog, movingUp=$movingUp, " +
              "fromIndex=$fromIndex, toIndex=$toIndex, compositeCatalogsCount=${compositeCatalogs.size}) " +
              "error", error)
          }
          .unwrap()

        val compositeCatalogDescriptor1 = compositeCatalogs[fromIndex]
        val compositeCatalogDescriptor2 = compositeCatalogs[toIndex]

        val temp = compositeCatalogs[fromIndex]
        compositeCatalogs[fromIndex] = compositeCatalogs[toIndex]
        compositeCatalogs[toIndex] = temp

        val event = Event.Swapped(
          compositeCatalogDescriptor1 = compositeCatalogDescriptor1.compositeCatalogDescriptor,
          compositeCatalogDescriptor2 = compositeCatalogDescriptor2.compositeCatalogDescriptor
        )

        updateCurrentCatalogDescriptorIfNeeded(event)
        _compositeCatalogUpdatesFlow.emit(event)
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
          .peekError { error -> Logger.e(TAG, "delete($compositeCatalog) error", error) }
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
      is Event.Swapped,
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
    data class Updated(val compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor) : Event()
    data class Deleted(val compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor) : Event()

    data class Swapped(
      val compositeCatalogDescriptor1: ChanDescriptor.CompositeCatalogDescriptor,
      val compositeCatalogDescriptor2: ChanDescriptor.CompositeCatalogDescriptor
    ) : Event()
  }

  companion object {
    private const val TAG = "CompositeCatalogManager"
  }
}