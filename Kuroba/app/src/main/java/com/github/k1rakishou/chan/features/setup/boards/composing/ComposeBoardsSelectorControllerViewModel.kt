package com.github.k1rakishou.chan.features.setup.boards.composing

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.features.setup.boards.selection.BoardSelectionControllerViewModel
import com.github.k1rakishou.chan.features.setup.data.CatalogCellData
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import javax.inject.Inject

class ComposeBoardsSelectorControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
) : KurobaViewModel() {

  private val currentlyComposedBoards = mutableSetOf<BoardDescriptor>()
  var searchQuery = mutableStateOf("")

  private val _cellDataList = mutableStateListOf<CellData>()
  val cellDataList: List<CellData>
    get() = _cellDataList

  val searchTextFieldState = TextFieldState()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {}

  fun reload(newCurrentlyComposedBoards: Set<BoardDescriptor>) {
    this.currentlyComposedBoards.clear()
    this.currentlyComposedBoards.addAll(newCurrentlyComposedBoards)

    val resultMap = linkedMapOf<SiteCellData, List<CatalogCellData>>()
    val activeSiteCount = siteManager.activeSiteCount()

    siteManager.viewActiveSitesOrderedWhile { chanSiteData, site ->
      if (site.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition)) {
        return@viewActiveSitesOrderedWhile true
      }

      if (site.configuration.catalogType == SiteConfiguration.CatalogType.Dynamic) {
        return@viewActiveSitesOrderedWhile true
      }

      val collectedBoards = collectBoards(searchQuery.value, chanSiteData, activeSiteCount)
      if (collectedBoards.isNotEmpty()) {
        val siteCellData = SiteCellData(
          siteDescriptor = chanSiteData.siteDescriptor,
          siteIcon = site.configuration.icon,
          siteName = site.name,
          siteEnableState = SiteEnableState.Active
        )

        resultMap[siteCellData] = collectedBoards
      }

      return@viewActiveSitesOrderedWhile true
    }

    _cellDataList.clear()

    resultMap.entries.forEach { (siteCellData, boardCellDataList) ->
      boardCellDataList.forEach { boardCellData ->
        _cellDataList.add(CellData(siteCellData, boardCellData))
      }
    }
  }

  private fun collectBoards(
    query: String,
    chanSiteData: ChanSiteData,
    activeSiteCount: Int
  ): List<CatalogCellData> {
    val boardCellDataList = mutableListOf<CatalogCellData>()

    val iteratorFunc = iteratorFunc@ { chanBoard: ChanBoard ->
      val boardCode = chanBoard.formattedBoardCode()
      val boardName = chanBoard.boardName()

      val matches = query.isEmpty()
        || boardCode.contains(query, ignoreCase = true)
        || boardName.contains(query, ignoreCase = true)

      if (matches && chanBoard.boardDescriptor !in currentlyComposedBoards) {
        boardCellDataList += CatalogCellData(
          searchQuery = query,
          catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(chanBoard.boardDescriptor),
          boardName = chanBoard.boardName(),
          description = ""
        )
      }

      return@iteratorFunc true
    }

    if (query.isEmpty()) {
      boardManager.viewBoardsWhile(
        boardViewMode = BoardManager.BoardViewMode.All,
        siteDescriptor = chanSiteData.siteDescriptor,
        func = iteratorFunc
      )

      return boardCellDataList
    }

    boardManager.viewBoardsWhile(
      boardViewMode = BoardManager.BoardViewMode.All,
      siteDescriptor = chanSiteData.siteDescriptor,
      func = iteratorFunc
    )

    val sortedBoards = InputWithQuerySorter.sort(
      input = boardCellDataList,
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
      BoardSelectionControllerViewModel.MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_TABLET
    } else {
      BoardSelectionControllerViewModel.MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_PHONE
    }

    return sortedBoards.take(maxBoardsToShow)
  }

  data class CellData(
    val siteCellData: SiteCellData,
    val catalogCellData: CatalogCellData
  ) {
    val siteName: String = catalogCellData.boardDescriptorOrNull!!.siteName()
    val boardCode: String = "/${catalogCellData.boardDescriptorOrNull!!.boardCode}/"
  }

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val boardManager: BoardManager,
  ) : ViewModelAssistedFactory<ComposeBoardsSelectorControllerViewModel> {
    override fun create(handle: SavedStateHandle): ComposeBoardsSelectorControllerViewModel {
      return ComposeBoardsSelectorControllerViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        boardManager = boardManager
      )
    }
  }

  companion object {
    private const val TAG = "ComposeBoardsSelectorControllerViewModel"
  }

}