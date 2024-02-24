package com.github.k1rakishou.chan.features.setup

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.ui.compose.reorder.move
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
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
    viewModelScope.launch {
      compositeCatalogManager.compositeCatalogUpdateEventsFlow
        .collect { event -> processCompositeCatalogUpdateEvents(event) }
    }
  }

  fun reload() {
    viewModelScope.launch { reloadInternal() }
  }

  suspend fun move(fromIndex: Int, toIndex: Int): ModularResult<Unit> {
    if (fromIndex == toIndex) {
      return ModularResult.value(Unit)
    }

    val result = compositeCatalogManager.move(fromIndex, toIndex)
      .peekError { error -> Logger.e(TAG, "move(fromIndex=$fromIndex, toIndex=$toIndex) error", error) }

    if (result is ModularResult.Value && fromIndex != toIndex) {
      val fromCatalog = _compositeCatalogs.getOrNull(fromIndex)
      val toCatalog = _compositeCatalogs.getOrNull(toIndex)

      if (fromCatalog != null && toCatalog != null) {
        _compositeCatalogs.move(fromIndex, toIndex)
      }
    }

    return result
  }

  suspend fun onMoveEnd(): ModularResult<Unit> {
    return compositeCatalogManager.persistAll()
      .peekError { error -> Logger.e(TAG, "persistAll() error", error) }
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
        is CompositeCatalogManager.Event.Updated -> {
          val prevCatalogDescriptor = event.prevCatalogDescriptor
          val newCatalogDescriptor = event.newCatalogDescriptor

          val updatedGlobalCompositeCatalog = globalCompositeCatalogs
            .firstOrNull { catalog -> catalog.compositeCatalogDescriptor == newCatalogDescriptor }

          if (updatedGlobalCompositeCatalog == null) {
            return@doWithLockedCompositeCatalogs
          }

          val indexOfPrevCompositeCatalog = _compositeCatalogs.indexOfFirst { catalog ->
            catalog.compositeCatalogDescriptor == prevCatalogDescriptor
          }

          if (indexOfPrevCompositeCatalog < 0) {
            return@doWithLockedCompositeCatalogs
          }

          // hack to update _compositeCatalogs list. We can't use set() here because composite catalog
          // descriptors has a custom equals method that compares sets of catalog descriptors
          // so if we move catalog descriptors around inside of a composite catalog descriptor
          // then using set() won't notify the listeners because it will think nothing has changed
          // (because oldList.equals(newList) will return false.
          _compositeCatalogs.removeAt(indexOfPrevCompositeCatalog)
          _compositeCatalogs.add(indexOfPrevCompositeCatalog, updatedGlobalCompositeCatalog)
        }
      }
    }
  }

  companion object {
    private const val TAG = "CompositeCatalogsSetupControllerViewModel"
  }

}