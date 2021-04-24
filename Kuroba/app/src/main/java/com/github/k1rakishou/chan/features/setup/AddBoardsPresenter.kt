package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.setup.data.AddBoardsControllerState
import com.github.k1rakishou.chan.features.setup.data.BoardCellData
import com.github.k1rakishou.chan.features.setup.data.SelectableBoardCellData
import com.github.k1rakishou.chan.ui.helper.BoardDescriptorsComparator
import com.github.k1rakishou.chan.ui.helper.BoardHelper
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

  private val selectedBoards = LinkedHashSet<BoardDescriptor>()

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

      if (query.isNotEmpty()) {
        if (!chanBoard.boardCode().contains(query, ignoreCase = true)) {
          return@viewBoards
        }
      }

      matchedBoards += SelectableBoardCellData(
        boardCellData = BoardCellData(
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

    val comparator = BoardDescriptorsComparator<SelectableBoardCellData>(query) { selectableBoardCellData ->
      selectableBoardCellData.boardCellData.boardDescriptor
    }

    val sortedBoards = try {
      matchedBoards.sortedWith(comparator)
    } catch (error: IllegalArgumentException) {
      val descriptors = matchedBoards.map { matchedBoard -> matchedBoard.boardCellData.boardDescriptor }
      throw IllegalAccessException("Bug in BoardDescriptorsComparator, query=$query, descriptors=${descriptors}")
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