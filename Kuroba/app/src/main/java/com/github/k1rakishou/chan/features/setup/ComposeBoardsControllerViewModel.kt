package com.github.k1rakishou.chan.features.setup

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.ui.compose.reorder.move
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import javax.inject.Inject

class ComposeBoardsControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val compositeCatalogManager: CompositeCatalogManager,
) : BaseViewModel() {

  private val _compositionSlots = mutableStateListOf<CatalogCompositionSlot>()
  val catalogCompositionSlots: List<CatalogCompositionSlot>
    get() = _compositionSlots

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  fun currentlyComposedBoards(): Set<BoardDescriptor> {
    return catalogCompositionSlots
      .mapNotNull { catalogCompositionSlot ->
        if (catalogCompositionSlot is CatalogCompositionSlot.Empty) {
          return@mapNotNull null
        }

        catalogCompositionSlot as CatalogCompositionSlot.Occupied
        return@mapNotNull catalogCompositionSlot.catalogDescriptor.boardDescriptor
      }
      .toSet()
  }

  fun resetCompositionSlots(compositeCatalog: CompositeCatalog?) {
    _compositionSlots.clear()

    repeat(ChanDescriptor.CompositeCatalogDescriptor.MAX_CATALOGS_COUNT) { index ->
      val catalogCompositionSlot = compositeCatalog
        ?.compositeCatalogDescriptor
        ?.catalogDescriptors
        ?.getOrNull(index)
        ?.let { catalogDescriptor -> CatalogCompositionSlot.Occupied(catalogDescriptor) }
        ?: CatalogCompositionSlot.Empty

      _compositionSlots.add(catalogCompositionSlot)
    }
  }

  fun updateSlot(clickedIndex: Int, boardDescriptor: BoardDescriptor?) {
    if (clickedIndex < 0 || clickedIndex >= _compositionSlots.size) {
      return
    }

    if (boardDescriptor != null) {
      _compositionSlots[clickedIndex] = CatalogCompositionSlot.Occupied(
        catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor)
      )
    } else {
      _compositionSlots[clickedIndex] = CatalogCompositionSlot.Empty
    }
  }

  fun clearSlot(clickedIndex: Int) {
    if (clickedIndex < 0 || clickedIndex >= _compositionSlots.size) {
      return
    }

    _compositionSlots[clickedIndex] = CatalogCompositionSlot.Empty
  }


  suspend fun alreadyExists(compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor): Boolean {
    return compositeCatalogManager.byCompositeCatalogDescriptor(compositeCatalogDescriptor) != null
  }

  fun move(fromIndex: Int, toIndex: Int) {
    _compositionSlots.getOrNull(fromIndex) ?: return
    _compositionSlots.getOrNull(toIndex) ?: return

    _compositionSlots.move(fromIndex, toIndex)
  }

  suspend fun createOrUpdateCompositeCatalog(
    newCompositeCatalogName: String,
    prevCompositeCatalog: CompositeCatalog?
  ): ModularResult<Unit> {
    val catalogDescriptors = _compositionSlots
      .toList()
      .mapNotNull { catalogCompositionSlot ->
        if (catalogCompositionSlot is CatalogCompositionSlot.Empty) {
          return@mapNotNull null
        }

        catalogCompositionSlot as CatalogCompositionSlot.Occupied
        return@mapNotNull catalogCompositionSlot.catalogDescriptor
      }

    if (catalogDescriptors.size < ChanDescriptor.CompositeCatalogDescriptor.MIN_CATALOGS_COUNT) {
      val errorMessage = "Not enough boards in CompositeCatalogDescriptor: " +
        "min ${ChanDescriptor.CompositeCatalogDescriptor.MIN_CATALOGS_COUNT}, " +
        "got: ${catalogDescriptors.size}"

      return ModularResult.error(CreateCompositeCatalogError(errorMessage))
    }

    if (catalogDescriptors.size > ChanDescriptor.CompositeCatalogDescriptor.MAX_CATALOGS_COUNT) {
      val errorMessage = "Too many boards in CompositeCatalogDescriptor: " +
        "max ${ChanDescriptor.CompositeCatalogDescriptor.MAX_CATALOGS_COUNT}, " +
        "got: ${catalogDescriptors.size}"

      return ModularResult.error(CreateCompositeCatalogError(errorMessage))
    }

    val compositeCatalog = CompositeCatalog(
      name = newCompositeCatalogName.take(MAX_COMPOSITE_CATALOG_TITLE_LENGTH),
      compositeCatalogDescriptor = ChanDescriptor.CompositeCatalogDescriptor.create(catalogDescriptors)
    )

    if (prevCompositeCatalog != null) {
      return compositeCatalogManager.update(compositeCatalog, prevCompositeCatalog)
    } else {
      return compositeCatalogManager.create(compositeCatalog)
    }
  }

  class CreateCompositeCatalogError(message: String) : Exception(message)

  sealed class CatalogCompositionSlot {
    data object Empty : CatalogCompositionSlot()
    data class Occupied(val catalogDescriptor: ChanDescriptor.CatalogDescriptor) : CatalogCompositionSlot()
  }

  class ViewModelFactory @Inject constructor(
    private val compositeCatalogManager: CompositeCatalogManager,
  ) : ViewModelAssistedFactory<ComposeBoardsControllerViewModel> {
    override fun create(handle: SavedStateHandle): ComposeBoardsControllerViewModel {
      return ComposeBoardsControllerViewModel(
        savedStateHandle = handle,
        compositeCatalogManager = compositeCatalogManager
      )
    }
  }

  companion object {
    private const val TAG = "ComposeBoardsControllerViewModel"
    const val MAX_COMPOSITE_CATALOG_TITLE_LENGTH = 18
  }

}