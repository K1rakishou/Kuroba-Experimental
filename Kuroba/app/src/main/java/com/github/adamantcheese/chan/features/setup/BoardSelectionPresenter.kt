package com.github.adamantcheese.chan.features.setup

import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.features.setup.data.BoardCellData
import com.github.adamantcheese.chan.features.setup.data.BoardSelectionControllerState
import com.github.adamantcheese.chan.features.setup.data.SiteCellData
import com.github.adamantcheese.chan.features.setup.data.SiteEnableState
import com.github.adamantcheese.chan.ui.helper.BoardHelper
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.errorMessageOrClassName
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
    val resultMap = linkedMapOf<SiteCellData, MutableList<BoardCellData>>()
    val viewActiveBoards = query.isEmpty()

    siteManager.viewActiveSitesOrdered { chanSiteData, site ->
      val siteCellData = SiteCellData(
        chanSiteData.siteDescriptor,
        site.icon().url.toString(),
        site.name(),
        SiteEnableState.Active
      )

      if (!resultMap.containsKey(siteCellData)) {
        resultMap[siteCellData] = mutableListOf()
      }

      boardManager.viewAllBoards(chanSiteData.siteDescriptor) { chanBoard ->
        if (viewActiveBoards && !chanBoard.active) {
          return@viewAllBoards
        }

        val boardName = BoardHelper.getName(chanBoard)

        if (query.isEmpty() || boardName.contains(query, ignoreCase = true)) {
          resultMap[siteCellData]!! += BoardCellData(
            chanBoard.boardDescriptor,
            boardName,
            ""
          )
        }
      }

      return@viewActiveSitesOrdered true
    }

    // TODO(KurobaEx): sort found boards

    if (resultMap.isEmpty()) {
      setState(BoardSelectionControllerState.Empty)
      return
    }

    setState(BoardSelectionControllerState.Data(resultMap))
  }

  private fun setState(state: BoardSelectionControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardSelectionPresenter"
  }
}