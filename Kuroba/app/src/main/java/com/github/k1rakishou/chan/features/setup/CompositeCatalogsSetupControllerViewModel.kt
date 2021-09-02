package com.github.k1rakishou.chan.features.setup

import androidx.compose.runtime.mutableStateListOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class CompositeCatalogsSetupControllerViewModel : BaseViewModel() {

  @Inject
  lateinit var compositeCatalogManager: CompositeCatalogManager

  private val _compositeCatalogs = mutableStateListOf<CompositeCatalog>()
  val compositeCatalogs: List<CompositeCatalog>
    get() = _compositeCatalogs

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    mainScope.launch {
      compositeCatalogManager.compositeCatalogUpdateEventsFlow
        .collect { event -> processCompositeCatalogUpdateEvents(event) }
    }
  }

  fun reload() {
    mainScope.launch { reloadInternal() }
  }

  suspend fun moveUp(movedCompositeCatalog: CompositeCatalog): ModularResult<Unit> {
    return compositeCatalogManager.move(movedCompositeCatalog, true)
      .peekError { error -> Logger.e(TAG, "moveUp($movedCompositeCatalog) error", error) }
  }

  suspend fun moveDown(movedCompositeCatalog: CompositeCatalog): ModularResult<Unit> {
    return compositeCatalogManager.move(movedCompositeCatalog, false)
      .peekError { error -> Logger.e(TAG, "moveDown($movedCompositeCatalog) error", error) }
  }

  suspend fun delete(compositeCatalog: CompositeCatalog): ModularResult<Unit> {
    return compositeCatalogManager.delete(compositeCatalog)
      .peekError { error -> Logger.e(TAG, "delete($compositeCatalog) error", error) }
  }

  private suspend fun reloadInternal() {
    _compositeCatalogs.clear()

    compositeCatalogManager.viewCatalogsOrdered { compositeCatalog ->
      _compositeCatalogs.add(compositeCatalog)
    }
  }

  private suspend fun processCompositeCatalogUpdateEvents(event: CompositeCatalogManager.Event) {
    compositeCatalogManager.doWithLockedCompositeCatalogs { globalCompositeCatalogs ->
      when (event) {
        is CompositeCatalogManager.Event.Created -> {
          val descriptor = event.compositeCatalogDescriptor
          val globalCompositeCatalog = globalCompositeCatalogs
            .firstOrNull { catalog -> catalog.compositeCatalogDescriptor == descriptor }

          if (globalCompositeCatalog == null) {
            return@doWithLockedCompositeCatalogs
          }

          val existing = _compositeCatalogs.firstOrNull { catalog ->
            catalog.compositeCatalogDescriptor == descriptor
          }

          if (existing != null) {
            return@doWithLockedCompositeCatalogs
          }

          _compositeCatalogs.add(globalCompositeCatalog)
        }
        is CompositeCatalogManager.Event.Deleted -> {
          val descriptor = event.compositeCatalogDescriptor

          _compositeCatalogs
            .removeIfKt { catalog -> catalog.compositeCatalogDescriptor == descriptor  }
        }
        is CompositeCatalogManager.Event.Swapped -> {
          val descriptor1 = event.compositeCatalogDescriptor1
          val descriptor2 = event.compositeCatalogDescriptor2

          if (descriptor1 == descriptor2) {
            return@doWithLockedCompositeCatalogs
          }

          val globalCompositeCatalog1 = globalCompositeCatalogs
            .firstOrNull { catalog -> catalog.compositeCatalogDescriptor == descriptor1 }
          val globalCompositeCatalog2 = globalCompositeCatalogs
            .firstOrNull { catalog -> catalog.compositeCatalogDescriptor == descriptor2 }

          if (globalCompositeCatalog1 == null || globalCompositeCatalog2 == null) {
            return@doWithLockedCompositeCatalogs
          }

          val indexOfExisting1 = _compositeCatalogs
            .indexOfFirst { catalog -> catalog.compositeCatalogDescriptor == descriptor1 }
          val indexOfExisting2 = _compositeCatalogs
            .indexOfFirst { catalog -> catalog.compositeCatalogDescriptor == descriptor2 }

          val temp = _compositeCatalogs[indexOfExisting2]
          _compositeCatalogs[indexOfExisting2] = _compositeCatalogs[indexOfExisting1]
          _compositeCatalogs[indexOfExisting1] = temp
        }
        is CompositeCatalogManager.Event.Updated -> {
          val descriptor = event.compositeCatalogDescriptor
          val globalCompositeCatalog = globalCompositeCatalogs
            .firstOrNull { catalog -> catalog.compositeCatalogDescriptor == descriptor }

          if (globalCompositeCatalog == null) {
            return@doWithLockedCompositeCatalogs
          }

          val indexOfExisting = _compositeCatalogs.indexOfFirst { catalog ->
            catalog.compositeCatalogDescriptor == descriptor
          }

          if (indexOfExisting < 0) {
            return@doWithLockedCompositeCatalogs
          }

          _compositeCatalogs[indexOfExisting] = globalCompositeCatalog
        }
      }
    }
  }

  companion object {
    private const val TAG = "CompositeCatalogsSetupControllerViewModel"
  }

}