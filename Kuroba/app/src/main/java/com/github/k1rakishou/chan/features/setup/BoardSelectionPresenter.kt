package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.BoardCellData
import com.github.k1rakishou.chan.features.setup.data.BoardSelectionControllerState
import com.github.k1rakishou.chan.features.setup.data.SiteCellData
import com.github.k1rakishou.chan.features.setup.data.SiteEnableState
import com.github.k1rakishou.chan.ui.helper.BoardDescriptorsComparator
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.site.ChanSiteData
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.launch
import javax.inject.Inject

class BoardSelectionPresenter : BasePresenter<BoardSelectionView>() {
  private val stateSubject = PublishProcessor.create<BoardSelectionControllerState>()
    .toSerialized()

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  override fun onCreate(view: BoardSelectionView) {
    super.onCreate(view)
    Chan.inject(this)

    setState(BoardSelectionControllerState.Loading)

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
    setState(BoardSelectionControllerState.Loading)

    showActiveSitesWithBoardsSorted(query)
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
        SiteEnableState.Active
      )

      resultMap[siteCellData] = collectBoards(query, chanSiteData)
      return@viewActiveSitesOrderedWhile true
    }

    if (resultMap.isEmpty()) {
      setState(BoardSelectionControllerState.Empty)
      return
    }

    setState(BoardSelectionControllerState.Data(resultMap))
  }

  private fun collectBoards(
    query: String,
    chanSiteData: ChanSiteData
  ): List<BoardCellData> {
    val boardCellDataList = mutableListOf<BoardCellData>()

    val iteratorFunc = { chanBoard: ChanBoard ->
      val boardCode = chanBoard.boardCode()

      if (query.isEmpty() || boardCode.contains(query, ignoreCase = true)) {
        boardCellDataList += BoardCellData(
          chanBoard.boardDescriptor,
          BoardHelper.getName(chanBoard),
          ""
        )
      }
    }

    if (query.isEmpty()) {
      boardManager.viewBoardsOrdered(chanSiteData.siteDescriptor, true, iteratorFunc)
      return boardCellDataList
    }

    boardManager.viewAllBoards(chanSiteData.siteDescriptor, iteratorFunc)

    val comparator = BoardDescriptorsComparator<BoardCellData>(query) { boardCellData ->
      boardCellData.boardDescriptor
    }

    return try {
      boardCellDataList.sortedWith(comparator)
    } catch (error: IllegalArgumentException) {
      val descriptors = boardCellDataList.map { boardCellData -> boardCellData.boardDescriptor }
      throw IllegalAccessException("Bug in BoardDescriptorsComparator, query=$query, descriptors=${descriptors}")
    }
  }

  private fun setState(state: BoardSelectionControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardSelectionPresenter"
  }
}