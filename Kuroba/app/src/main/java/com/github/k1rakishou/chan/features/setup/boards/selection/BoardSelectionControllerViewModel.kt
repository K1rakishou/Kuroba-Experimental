package com.github.k1rakishou.chan.features.setup.boards.selection

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.concurrency.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BoardSelectionControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val compositeCatalogManager: CompositeCatalogManager,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
) : KurobaViewModel() {
  private val _selectableElements = mutableStateListOf<SelectableElement>()
  val selectableElements: SnapshotStateList<SelectableElement>
    get() = _selectableElements

  private val _currentSearchQuery = mutableStateOf("")
  val currentSearchQuery: State<String>
    get() = _currentSearchQuery

  val currentlySelectedCatalogDescriptor: StateFlow<ChanDescriptor.ICatalogDescriptor?>
    get() = currentOpenedDescriptorStateManager.currentCatalogDescriptorFlow

  private var _searchQueryUpdateExecutor = DebouncingCoroutineExecutor(viewModelScope)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    siteManager.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()

    viewModelScope.launch {
      showActiveSitesWithBoardsSorted()
    }

    viewModelScope.launch {
      boardManager.eventsFlow
        .onEach { event ->
          val needReloadBoards = when (event) {
            is BoardManager.Event.ActivatedOrDeactivate -> true
            is BoardManager.Event.Move -> true
          }

          if (needReloadBoards) {
            showActiveSitesWithBoardsSorted(query = _currentSearchQuery.value)
          }
        }
        .collect()
    }

    viewModelScope.launch {
      compositeCatalogManager.eventsFlow
        .onEach { showActiveSitesWithBoardsSorted(query = _currentSearchQuery.value) }
        .collect()
    }
  }

  fun onSearchQueryChanged(query: String) {
    _searchQueryUpdateExecutor.post(200) {
      showActiveSitesWithBoardsSorted(query)
    }
  }

  private suspend fun showActiveSitesWithBoardsSorted(query: String = "") {
    withContext(Dispatchers.Default) {
      val siteHeaders = mutableListOf<SelectableElement.SiteHeader>()
      val activeSiteCount = siteManager.activeSiteCount()

      siteManager.viewActiveSitesOrderedWhile { chanSiteData, site ->
        siteHeaders += SelectableElement.SiteHeader(
          siteDescriptor = chanSiteData.siteDescriptor,
          name = site.name
        )

        return@viewActiveSitesOrderedWhile true
      }

      val elements = mutableListWithCap<SelectableElement>(initialCapacity = 128)
      siteHeaders.forEach { siteHeader ->
        val site = siteManager.bySiteDescriptorAndActive(siteHeader.siteDescriptor)
          ?: return@forEach

        val boards = if (site.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition)) {
          collectBoardsFromCompositeCatalogs(query, activeSiteCount)
        } else {
          collectBoardsFromBoardManager(query, siteHeader.siteDescriptor, activeSiteCount)
        }

        elements.add(siteHeader)
        elements.add(SelectableElement.Boards(siteHeader.siteDescriptor, boards))
      }

      Snapshot.withMutableSnapshot {
        _selectableElements.clear()
        _selectableElements.addAll(elements)

        _currentSearchQuery.value = query
      }
    }
  }

  private suspend fun collectBoardsFromCompositeCatalogs(
    query: String,
    activeSiteCount: Int
  ): List<SelectableBoard> {
    val selectableBoards = mutableListOf<SelectableBoard>()

    val iteratorFunc = iteratorFunc@{ compositeCatalog: CompositeCatalog ->
      val boardCodes = compositeCatalog.compositeCatalogDescriptor.userReadableString()
      val compositeCatalogName = compositeCatalog.name

      val matches = query.isEmpty()
        || boardCodes.contains(query, ignoreCase = true)
        || compositeCatalogName.contains(query, ignoreCase = true)

      if (!matches) {
        return@iteratorFunc
      }

      selectableBoards += SelectableBoard(
        catalogDescriptor = compositeCatalog.compositeCatalogDescriptor,
        header = compositeCatalogName,
        description = boardCodes.takeIf { str -> str.isNotBlank() },
        safeForWork = null
      )
    }

    if (query.isEmpty()) {
      compositeCatalogManager.viewCatalogsOrdered(iteratorFunc)
      return selectableBoards
    }

    compositeCatalogManager.viewCatalogsOrdered(iteratorFunc)

    val sortedCatalogCellData = InputWithQuerySorter.sort(
      input = selectableBoards,
      query = query,
      textSelector = { catalogCellData ->
        return@sort when (catalogCellData.catalogDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> {
            catalogCellData.catalogDescriptor.boardCode()
          }
          is ChanDescriptor.CompositeCatalogDescriptor -> {
            catalogCellData.catalogDescriptor.userReadableString()
          }
        }
      }
    )

    if (query.isEmpty() || activeSiteCount <= 1) {
      return sortedCatalogCellData
    }

    val maxBoardsToShow = if (AppModuleAndroidUtils.isTablet) {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_TABLET
    } else {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_PHONE
    }

    return sortedCatalogCellData.take(maxBoardsToShow)
  }

  private suspend fun collectBoardsFromBoardManager(
    query: String,
    siteDescriptor: SiteDescriptor,
    activeSiteCount: Int
  ): List<SelectableBoard> {
    boardManager.awaitUntilInitialized()

    val selectableBoards = mutableListWithCap<SelectableBoard>(initialCapacity = 512)

    val iteratorFunc = iteratorFunc@{ chanBoard: ChanBoard ->
      val boardCode = chanBoard.formattedBoardCode()
      val boardName = chanBoard.boardName()

      val matches = query.isEmpty()
        || boardCode.contains(query, ignoreCase = true)
        || boardName.contains(query, ignoreCase = true)

      if (matches) {
        selectableBoards += SelectableBoard(
          catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(chanBoard.boardDescriptor),
          header = "/${chanBoard.boardDescriptor.boardCode}/",
          description = chanBoard.boardName(),
          safeForWork = chanBoard.workSafe
        )
      }

      return@iteratorFunc true
    }

    if (query.isEmpty()) {
      boardManager.viewBoardsOrdered(
        siteDescriptor = siteDescriptor,
        boardViewMode = BoardManager.BoardViewMode.Active,
        func = iteratorFunc
      )
      return selectableBoards
    }

    boardManager.viewBoardsWhile(
      boardViewMode = BoardManager.BoardViewMode.All,
      siteDescriptor = siteDescriptor,
      func = iteratorFunc
    )

    var canFilterOutLastElement = true
    if (query.isNotEmpty()) {
      val boardCode = query.filter { ch -> ch.isLetterOrDigit() }
      if (boardCode.isNotNullNorBlank()) {
        val alreadyContainsThisBoard = selectableBoards
          .any { selectableBoard -> selectableBoard.boardDescriptor?.boardCode == boardCode }

        if (!alreadyContainsThisBoard) {
          val boardDescriptor = BoardDescriptor.create(siteDescriptor, boardCode)

          val selectableBoard = SelectableBoard(
            catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor),
            header = "/${query}/",
            description = null,
            safeForWork = null
          )

          selectableBoards += selectableBoard
          canFilterOutLastElement = false
        }
      }
    }

    val sortedBoards = InputWithQuerySorter.sort(
      input = selectableBoards,
      query = query,
      textSelector = { boardCellData ->
        return@sort when (boardCellData.catalogDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> {
            boardCellData.catalogDescriptor.boardCode()
          }
          is ChanDescriptor.CompositeCatalogDescriptor -> {
            boardCellData.catalogDescriptor.userReadableString()
          }
        }
      }
    )

    if (query.isEmpty() || activeSiteCount <= 1) {
      return sortedBoards
    }

    val maxBoardsToShow = if (AppModuleAndroidUtils.isTablet) {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_TABLET
    } else {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_PHONE
    }

    if (canFilterOutLastElement || sortedBoards.size <= maxBoardsToShow) {
      return sortedBoards.take(maxBoardsToShow)
    }

    return sortedBoards.take(maxBoardsToShow) + sortedBoards.last()
  }

  sealed interface SelectableElement {
    data class SiteHeader(
      val siteDescriptor: SiteDescriptor,
      val name: String,
    ) : SelectableElement

    data class Boards(
      val siteDescriptor: SiteDescriptor,
      val selectableBoards: List<SelectableBoard>
    ) : SelectableElement
  }

  data class SelectableBoard(
    val catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
    val header: String,
    val description: String?,
    val safeForWork: Boolean?
  ) {
    val boardDescriptor: BoardDescriptor?
      get() {
        return when (catalogDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> catalogDescriptor.boardDescriptor
          is ChanDescriptor.CompositeCatalogDescriptor -> null
        }
      }
  }

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val boardManager: BoardManager,
    private val compositeCatalogManager: CompositeCatalogManager,
    private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
  ) : ViewModelAssistedFactory<BoardSelectionControllerViewModel> {
    override fun create(handle: SavedStateHandle): BoardSelectionControllerViewModel {
      return BoardSelectionControllerViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        boardManager = boardManager,
        compositeCatalogManager = compositeCatalogManager,
        currentOpenedDescriptorStateManager = currentOpenedDescriptorStateManager,
      )
    }
  }

  companion object {
    const val MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_PHONE = 5
    const val MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_TABLET = 10
  }
}