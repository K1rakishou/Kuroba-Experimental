package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.AddBoardsControllerState
import com.github.k1rakishou.chan.features.setup.data.BoardCellData
import com.github.k1rakishou.chan.features.setup.data.SelectableBoardCellData
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddBoardsPresenter(
  private val siteDescriptor: SiteDescriptor,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
) : BasePresenter<AddBoardsView>() {
  private val stateSubject = PublishProcessor.create<AddBoardsControllerState>()
    .toSerialized()

  private val selectedBoards = mutableListOf<BoardDescriptor>()

  override fun onCreate(view: AddBoardsView) {
    super.onCreate(view)

    selectedBoards.clear()
    showInactiveBoards()
  }

  fun listenForStateChanges(): Flowable<AddBoardsControllerState> {
    return stateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to stateSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> AddBoardsControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  private fun showInactiveBoards() {
    scope.launch(Dispatchers.Default) {
      setState(AddBoardsControllerState.Loading)

      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptor(siteDescriptor)
      if (site == null) {
        setState(AddBoardsControllerState.Error("No site found by descriptor: ${siteDescriptor}"))
        return@launch
      }

      val isSiteActive = siteManager.isSiteActive(siteDescriptor)
      if (!isSiteActive) {
        setState(AddBoardsControllerState.Error("Site with descriptor ${siteDescriptor} is not active!"))
        return@launch
      }

      showBoardsWithSearchQuery()
    }
  }

  fun onBoardSelectionChanged(boardDescriptor: BoardDescriptor, checked: Boolean, query: String) {
    if (checked) {
      selectedBoards += boardDescriptor
    } else {
      selectedBoards -= boardDescriptor
    }

    showBoardsWithSearchQuery(query)
  }

  suspend fun addSelectedBoards() {
    boardManager.activateDeactivateBoards(siteDescriptor, selectedBoards, true)

    selectedBoards.clear()
  }

  fun onSearchQueryChanged(query: String) {
    setState(AddBoardsControllerState.Loading)

    showBoardsWithSearchQuery(query)
  }

  fun checkUncheckAll(query: String) {
    val selectAll = selectedBoards.size != boardManager.getTotalCount(onlyActive = false)

    if (selectAll) {
      boardManager.viewBoards(siteDescriptor, BoardManager.BoardViewMode.OnlyNonActiveBoards) { chanBoard ->
        selectedBoards.add(chanBoard.boardDescriptor)
      }

      selectedBoards
        .sortBy { selectedBoard -> selectedBoard.boardCode }
    } else {
      selectedBoards.clear()
    }

    showBoardsWithSearchQuery(query, selectAllBoards = selectAll)
  }

  private fun showBoardsWithSearchQuery(query: String = "", selectAllBoards: Boolean = false) {
    val matchedBoards = mutableListWithCap<SelectableBoardCellData>(32)

    boardManager.viewBoards(siteDescriptor, BoardManager.BoardViewMode.OnlyNonActiveBoards) { chanBoard ->
      val isSelected = if (selectAllBoards) {
        selectedBoards.isNotEmpty()
      } else {
        chanBoard.boardDescriptor in selectedBoards
      }

      val boardCode = chanBoard.formattedBoardCode()
      val boardName = chanBoard.boardName()
      val boardDescription = chanBoard.description

      val matches = query.isEmpty()
        || boardCode.contains(query, ignoreCase = true)
        || boardName.contains(query, ignoreCase = true)
        || (boardDescription.isEmpty() || boardDescription.contains(query, ignoreCase = true))

      if (!matches) {
        return@viewBoards
      }

      matchedBoards += SelectableBoardCellData(
        boardCellData = BoardCellData(
          searchQuery = query,
          boardDescriptor = chanBoard.boardDescriptor,
          boardName = chanBoard.boardName(),
          description = BoardHelper.getDescription(chanBoard)
        ),
        selected = isSelected
      )
    }

    if (matchedBoards.isEmpty()) {
      setState(AddBoardsControllerState.Empty)
      return
    }

    val sortedBoards = if (query.isEmpty()) {
      matchedBoards
        .sortedBy { matchedBoard -> matchedBoard.boardCellData.boardDescriptor.boardCode }
    } else {
      InputWithQuerySorter.sort(
        input = matchedBoards,
        query = query,
        textSelector = { selectableBoardCellData -> selectableBoardCellData.boardCellData.boardDescriptor.boardCode }
      )
    }

    setState(AddBoardsControllerState.Data(sortedBoards))
  }

  private fun setState(state: AddBoardsControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "AddBoardsPresenter"
  }
}