package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.BoardCellData
import com.github.k1rakishou.chan.features.setup.data.BoardSelectionControllerState
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.persist_state.PersistableChanState
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.launch

class BoardSelectionPresenter(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val archivesManager: ArchivesManager,
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

  fun onSearchQueryChanged(query: String) {
    showActiveSitesWithBoardsSorted(query)
  }

  fun reloadBoards() {
    showActiveSitesWithBoardsSorted()
  }

  private fun showActiveSitesWithBoardsSorted(query: String = "") {
    val resultMap = linkedMapOf<SiteCellData, List<BoardCellData>>()
    val activeSiteCount = siteManager.activeSiteCount()

    siteManager.viewActiveSitesOrderedWhile { chanSiteData, site ->
      val siteCellData = SiteCellData(
        siteDescriptor = chanSiteData.siteDescriptor,
        siteIcon = site.icon().url.toString(),
        siteName = site.name(),
        siteEnableState = SiteEnableState.Active
      )

      val collectedBoards = collectBoards(query, chanSiteData, activeSiteCount)
      if (collectedBoards.isNotEmpty()) {
        resultMap[siteCellData] = collectedBoards
      }

      return@viewActiveSitesOrderedWhile true
    }

    if (resultMap.isEmpty()) {
      setState(BoardSelectionControllerState.Empty)
      return
    }

    val newState = BoardSelectionControllerState.Data(
      isGridMode = PersistableChanState.boardSelectionGridMode.get(),
      sortedSiteWithBoardsData = resultMap,
      currentlySelected = currentOpenedDescriptorStateManager.currentCatalogDescriptor?.boardDescriptor
    )

    setState(newState)
  }

  private fun collectBoards(
    query: String,
    chanSiteData: ChanSiteData,
    activeSiteCount: Int
  ): List<BoardCellData> {
    val boardCellDataList = mutableListOf<BoardCellData>()

    val iteratorFunc = iteratorFunc@ { chanBoard: ChanBoard ->
      val boardCode = chanBoard.formattedBoardCode()
      val boardName = chanBoard.boardName()

      val matches = query.isEmpty()
        || boardCode.contains(query, ignoreCase = true)
        || boardName.contains(query, ignoreCase = true)

      if (!matches) {
        return@iteratorFunc
      }

      boardCellDataList += BoardCellData(
        searchQuery = query,
        boardDescriptor = chanBoard.boardDescriptor,
        boardName = chanBoard.boardName(),
        description = ""
      )
    }

    if (query.isEmpty()) {
      boardManager.viewBoardsOrdered(chanSiteData.siteDescriptor, true, iteratorFunc)
      return boardCellDataList
    }

    boardManager.viewAllBoards(chanSiteData.siteDescriptor, iteratorFunc)

    val sortedBoards = InputWithQuerySorter.sort(
      input = boardCellDataList,
      query = query,
      textSelector = { boardCellData -> boardCellData.boardDescriptor.boardCode }
    )

    if (query.isEmpty() || activeSiteCount <= 1) {
      return sortedBoards
    }

    val maxBoardsToShow = if (isTablet()) {
      MAX_BOARDS_TO_SHOW_IN_SEARCH_MODE_TABLET
    } else {
      MAX_BOARDS_TO_SHOW_IN_SEARCH_MODE_PHONE
    }

    return sortedBoards.take(maxBoardsToShow)
  }

  private fun setState(state: BoardSelectionControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardSelectionPresenter"
    private const val MAX_BOARDS_TO_SHOW_IN_SEARCH_MODE_PHONE = 5
    private const val MAX_BOARDS_TO_SHOW_IN_SEARCH_MODE_TABLET = 10
  }
}