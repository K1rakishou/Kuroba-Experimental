package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.setup.data.BoardSelectionControllerState
import com.github.k1rakishou.chan.features.setup.data.CatalogCellData
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.launch

class BoardSelectionPresenter(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val compositeCatalogManager: CompositeCatalogManager,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
) : BasePresenter<BoardSelectionView>() {
  private val stateSubject = PublishProcessor.create<BoardSelectionControllerState>()
    .toSerialized()

  override fun onCreate(view: BoardSelectionView) {
    super.onCreate(view)

    scope.launch {
      siteManager.awaitUntilInitialized()
      boardManager.awaitUntilInitialized()

      showActiveSitesWithBoardsSorted()
    }
  }

  fun listenForStateChanges(): Flowable<BoardSelectionControllerState> {
    return stateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to stateSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> BoardSelectionControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  suspend fun onSearchQueryChanged(query: String) {
    showActiveSitesWithBoardsSorted(query)
  }

  fun reloadBoards() {
    scope.launch { showActiveSitesWithBoardsSorted() }
  }

  private suspend fun showActiveSitesWithBoardsSorted(query: String = "") {
    val siteCellDataList = mutableListOf<SiteCellData>()
    val resultMap = linkedMapOf<SiteCellData, List<CatalogCellData>>()
    val activeSiteCount = siteManager.activeSiteCount()

    siteManager.viewActiveSitesOrderedWhile { chanSiteData, site ->
      siteCellDataList += SiteCellData(
        siteDescriptor = chanSiteData.siteDescriptor,
        siteIcon = site.icon(),
        siteName = site.name(),
        siteEnableState = SiteEnableState.Active
      )

      return@viewActiveSitesOrderedWhile true
    }

    siteCellDataList.forEach { siteCellData ->
      val site = siteManager.bySiteDescriptor(siteCellData.siteDescriptor)
        ?: return@forEach

      val collectedCatalogCellData = if (site.siteFeature(Site.SiteFeature.CATALOG_COMPOSITION)) {
        collectCatalogCellDataFromCompositeCatalogs(query, activeSiteCount)
      } else {
        collectCatalogCellDataFromBoards(query, siteCellData.siteDescriptor, activeSiteCount)
      }

      if (collectedCatalogCellData.isNotEmpty()) {
        resultMap[siteCellData] = collectedCatalogCellData
      }
    }

    if (resultMap.isEmpty()) {
      setState(BoardSelectionControllerState.Empty)
      return
    }

    val newState = BoardSelectionControllerState.Data(
      isGridMode = PersistableChanState.boardSelectionGridMode.get(),
      sortedSiteWithBoardsData = resultMap,
      currentlySelected = currentOpenedDescriptorStateManager.currentCatalogDescriptor
    )

    setState(newState)
  }

  private suspend fun collectCatalogCellDataFromCompositeCatalogs(
    query: String,
    activeSiteCount: Int
  ): List<CatalogCellData> {
    val catalogCellDataList = mutableListOf<CatalogCellData>()

    val iteratorFunc = iteratorFunc@ { compositeCatalog: CompositeCatalog ->
      val boardCodes = compositeCatalog.compositeCatalogDescriptor.userReadableString()
      val compositeCatalogName = compositeCatalog.name

      val matches = query.isEmpty()
        || boardCodes.contains(query, ignoreCase = true)
        || compositeCatalogName.contains(query, ignoreCase = true)

      if (!matches) {
        return@iteratorFunc
      }

      catalogCellDataList += CatalogCellData(
        searchQuery = query,
        catalogDescriptor = compositeCatalog.compositeCatalogDescriptor,
        boardName = compositeCatalogName,
        description = boardCodes
      )
    }

    if (query.isEmpty()) {
      compositeCatalogManager.viewCatalogsOrdered(iteratorFunc)
      return catalogCellDataList
    }

    compositeCatalogManager.viewCatalogsOrdered(iteratorFunc)

    val sortedCatalogCellData = InputWithQuerySorter.sort(
      input = catalogCellDataList,
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

    val maxBoardsToShow = if (isTablet()) {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_TABLET
    } else {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_PHONE
    }

    return sortedCatalogCellData.take(maxBoardsToShow)
  }

  private fun collectCatalogCellDataFromBoards(
    query: String,
    siteDescriptor: SiteDescriptor,
    activeSiteCount: Int
  ): List<CatalogCellData> {
    val boardCellDataList = mutableListOf<CatalogCellData>()

    val iteratorFunc = iteratorFunc@ { chanBoard: ChanBoard ->
      val boardCode = chanBoard.formattedBoardCode()
      val boardName = chanBoard.boardName()

      val matches = query.isEmpty()
        || boardCode.contains(query, ignoreCase = true)
        || boardName.contains(query, ignoreCase = true)

      if (!matches) {
        return@iteratorFunc
      }

      boardCellDataList += CatalogCellData(
        searchQuery = query,
        catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(chanBoard.boardDescriptor),
        boardName = chanBoard.boardName(),
        description = ""
      )
    }

    if (query.isEmpty()) {
      boardManager.viewActiveBoardsOrdered(siteDescriptor, iteratorFunc)
      return boardCellDataList
    }

    boardManager.viewAllBoards(siteDescriptor, iteratorFunc)

    var canFilterOutLastElement = true
    if (query.isNotEmpty()) {
      val boardCode = query.filter { ch -> ch.isLetterOrDigit() }
      if (boardCode.isNotNullNorBlank()) {
        val alreadyContainsThisBoard = boardCellDataList.any { it.boardDescriptorOrNull?.boardCode == boardCode }
        if (!alreadyContainsThisBoard) {
          val boardDescriptor = BoardDescriptor.create(siteDescriptor, boardCode)

          val cellDataList = CatalogCellData(
            searchQuery = query,
            catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor),
            boardName = "",
            description = ""
          )

          boardCellDataList += cellDataList
          canFilterOutLastElement = false
        }
      }
    }

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

    val maxBoardsToShow = if (isTablet()) {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_TABLET
    } else {
      MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_PHONE
    }

    if (canFilterOutLastElement || sortedBoards.size <= maxBoardsToShow) {
      return sortedBoards.take(maxBoardsToShow)
    }

    return sortedBoards.take(maxBoardsToShow) + sortedBoards.last()
  }

  private fun setState(state: BoardSelectionControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardSelectionPresenter"
    const val MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_PHONE = 5
    const val MAX_CATALOGS_TO_SHOW_IN_SEARCH_MODE_TABLET = 10
  }
}