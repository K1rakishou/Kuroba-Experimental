package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.BoardCellData
import com.github.k1rakishou.chan.features.setup.data.BoardSelectionControllerState
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
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

    siteManager.viewActiveSitesOrderedWhile { chanSiteData, site ->
      if (archivesManager.isSiteArchive(site.siteDescriptor())) {
        // Skip archives since they have no boards (at least for now)
        return@viewActiveSitesOrderedWhile true
      }

      val siteCellData = SiteCellData(
        chanSiteData.siteDescriptor,
        site.icon().url.toString(),
        site.name(),
        SiteEnableState.Active,
        null
      )

      resultMap[siteCellData] = collectBoards(query, chanSiteData)
      return@viewActiveSitesOrderedWhile true
    }

    if (resultMap.isEmpty()) {
      setState(BoardSelectionControllerState.Empty)
      return
    }

    val newState = BoardSelectionControllerState.Data(
      isGridMode = PersistableChanState.boardSelectionGridMode.get(),
      sortedSiteWithBoardsData = resultMap
    )

    setState(newState)
  }

  private fun collectBoards(
    query: String,
    chanSiteData: ChanSiteData
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

    return boardCellDataList.sortedBy { boardCellData -> boardCellData.boardDescriptor.boardCode }
  }

  private fun setState(state: BoardSelectionControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardSelectionPresenter"
  }
}