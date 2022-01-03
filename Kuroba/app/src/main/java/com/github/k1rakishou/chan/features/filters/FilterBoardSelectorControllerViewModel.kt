package com.github.k1rakishou.chan.features.filters

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.setup.data.CatalogCellData
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import javax.inject.Inject

class FilterBoardSelectorControllerViewModel  : BaseViewModel() {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager

  private var allBoardsSelected: Boolean = false

  private val _currentlySelectedBoards = mutableStateMapOf<BoardDescriptor, Unit>()
  val currentlySelectedBoards: Map<BoardDescriptor, Unit>
    get() = _currentlySelectedBoards

  private val _cellDataList = mutableStateListOf<CellData>()
  val cellDataList: List<CellData>
    get() = _cellDataList

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {}

  fun reload(newCurrentlySelectedBoards: Set<BoardDescriptor>?) {
    val resultMap = linkedMapOf<SiteCellData, List<CatalogCellData>>()

    this._currentlySelectedBoards.clear()
    this.allBoardsSelected = newCurrentlySelectedBoards == null

    siteManager.viewActiveSitesOrderedWhile { chanSiteData, site ->
      if (site.siteFeature(Site.SiteFeature.CATALOG_COMPOSITION)) {
        return@viewActiveSitesOrderedWhile true
      }

      val collectedBoards = collectBoards(chanSiteData)
      if (collectedBoards.isNotEmpty()) {
        val siteCellData = SiteCellData(
          siteDescriptor = chanSiteData.siteDescriptor,
          siteIcon = site.icon(),
          siteName = site.name(),
          siteEnableState = SiteEnableState.Active
        )

        resultMap[siteCellData] = collectedBoards
      }

      return@viewActiveSitesOrderedWhile true
    }

    if (newCurrentlySelectedBoards != null) {
      newCurrentlySelectedBoards.forEach { boardDescriptor ->
        this._currentlySelectedBoards.put(boardDescriptor, Unit)
      }
    } else {
      resultMap.entries.forEach { (_, boardCellDataList) ->
        boardCellDataList.forEach { boardCellData ->
          this._currentlySelectedBoards.put(boardCellData.requireBoardDescriptor(), Unit)
        }
      }
    }

    _cellDataList.clear()

    resultMap.entries.forEach { (siteCellData, boardCellDataList) ->
      boardCellDataList.forEach { boardCellData ->
        _cellDataList.add(CellData(siteCellData, boardCellData))
      }
    }
  }

  fun onBoardSelectionToggled(boardDescriptor: BoardDescriptor) {
    if (_currentlySelectedBoards.contains(boardDescriptor)) {
      _currentlySelectedBoards.remove(boardDescriptor)
    } else {
      _currentlySelectedBoards.put(boardDescriptor, Unit)
    }
  }

  fun toggleSelectUnselectAll(searchResults: List<CellData>) {
    val displayedBoardsDescriptors = searchResults
      .map { boardDescriptor -> boardDescriptor.catalogCellData.requireBoardDescriptor() }

    val allBoardsSelected = displayedBoardsDescriptors
      .all { boardDescriptor -> _currentlySelectedBoards.containsKey(boardDescriptor) }

    if (!allBoardsSelected) {
      displayedBoardsDescriptors.forEach { boardDescriptor ->
        _currentlySelectedBoards.put(boardDescriptor, Unit)
      }
    } else {
      displayedBoardsDescriptors.forEach { boardDescriptor ->
        _currentlySelectedBoards.remove(boardDescriptor, Unit)
      }
    }
  }

  private fun collectBoards(chanSiteData: ChanSiteData): List<CatalogCellData> {
    val boardCellDataList = mutableListOf<CatalogCellData>()

    val iteratorFunc = iteratorFunc@ { chanBoard: ChanBoard ->
      if (chanBoard.synthetic) {
        return@iteratorFunc
      }

      val boardDescriptor = chanBoard.boardDescriptor

      if (!chanBoard.active && !_currentlySelectedBoards.containsKey(boardDescriptor)) {
        // Handle a situation where a currently not active boards was previously selected for a filter
        return@iteratorFunc
      }

      boardCellDataList += CatalogCellData(
        searchQuery = null,
        catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(chanBoard.boardDescriptor),
        boardName = chanBoard.boardName(),
        description = ""
      )
    }

    boardManager.viewAllBoards(
      siteDescriptor = chanSiteData.siteDescriptor,
      func = iteratorFunc
    )

    val sortedBoards = InputWithQuerySorter.sort(
      input = boardCellDataList,
      query = "",
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

    return sortedBoards
  }

  data class CellData(
    val siteCellData: SiteCellData,
    val catalogCellData: CatalogCellData
  )

  companion object {
    private const val TAG = "FilterBoardSelectorControllerViewModel"
  }

}