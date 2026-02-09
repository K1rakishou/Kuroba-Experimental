package com.github.k1rakishou.chan.features.setup.boards.add

import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.concurrency.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.chan.utils.requireParams
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AddBoardsControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
) : BaseViewModel() {
  private val _allInactiveBoards = mutableListWithCap<ChanBoard>(initialCapacity = 1024)

  private val _checkedBoards = mutableStateSetOf<BoardDescriptor>()
  val checkedBoards: SnapshotStateSet<BoardDescriptor>
    get() = _checkedBoards

  private val _uiState = mutableStateOf<AsyncData<Unit>>(AsyncData.NotInitialized)
  val uiState: State<AsyncData<Unit>>
    get() = _uiState

  private val _boardsForSelection = mutableStateListOf<BoardForSelection>()
  val boardsForSelection: SnapshotStateList<BoardForSelection>
    get() = _boardsForSelection

  private val _currentSearchQuery = mutableStateOf("")
  val currentSearchQuery: State<String>
    get() = _currentSearchQuery

  private val _nonActiveBoardsCount = mutableIntStateOf(-1)
  val nonActiveBoardsCount: IntState
    get() = _nonActiveBoardsCount

  private val _resetScrollEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.RENDEZVOUS)
  val resetScrollEventFlow: SharedFlow<Unit>
    get() = _resetScrollEventFlow.asSharedFlow()

  private val _params = savedStateHandle.requireParams<AddBoardsControllerParams>()
  private val _siteDescriptor: SiteDescriptor
    get() = _params.siteDescriptor

  private val _searchQueryUpdateExecutor = DebouncingCoroutineExecutor(viewModelScope)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    _uiState.value = AsyncData.NotInitialized

    viewModelScope.launch(Dispatchers.Default) {
      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptorAndActive(_siteDescriptor)
      if (site == null) {
        _uiState.value = AsyncData.Error(Exception("No site found by descriptor: ${_siteDescriptor}"))
        return@launch
      }

      val isSiteActive = siteManager.isSiteActive(_siteDescriptor)
      if (!isSiteActive) {
        _uiState.value = AsyncData.Error(Exception("Site with descriptor ${_siteDescriptor} is not active!"))
        return@launch
      }

      _uiState.value = AsyncData.Loading

      loadInactiveBoards(_siteDescriptor)
      findBoardsForSelection()
    }
  }

  fun onSearchQueryUpdated(query: String) {
    if (query == _currentSearchQuery.value) {
      return
    }

    _searchQueryUpdateExecutor.post(timeout = 100L) { findBoardsForSelection(query) }
  }

  fun onBoardCheckStateChanged(boardDescriptor: BoardDescriptor, check: Boolean) {
    if (check) {
      _checkedBoards.add(boardDescriptor)
    } else {
      _checkedBoards.remove(boardDescriptor)
    }
  }

  fun toggleAll() {
    if (_checkedBoards.size == _allInactiveBoards.size) {
      _checkedBoards.clear()
      return
    }

    val allBoardsDescriptors = _allInactiveBoards
      .map { chanBoard -> chanBoard.boardDescriptor }

    _checkedBoards.clear()
    _checkedBoards.addAll(allBoardsDescriptors)
  }

  fun activateCheckedBoards(onDone: () -> Unit) {
    viewModelScope.launch {
      try {
        boardManager.activateDeactivateBoards(
          siteDescriptor = _siteDescriptor,
          boardDescriptors = checkedBoards.toList(),
          activate = true
        )
      } finally {
        onDone()
      }
    }
  }

  private fun loadInactiveBoards(siteDescriptor: SiteDescriptor) {
    _allInactiveBoards.clear()

    boardManager.viewBoards(
      boardViewMode = BoardManager.BoardViewMode.NonActive,
      siteDescriptor = siteDescriptor
    ) { chanBoard ->
      _allInactiveBoards.add(chanBoard)
      return@viewBoards true
    }

    _nonActiveBoardsCount.intValue = _allInactiveBoards.size
  }

  private suspend fun findBoardsForSelection(query: String = "") {
    return withContext(Dispatchers.Default) {
      val matchedBoards = mutableListWithCap<BoardForSelection>(MAX_DISPLAYED_BOARDS)

      for (chanBoard in _allInactiveBoards) {
        if (matchedBoards.size >= MAX_DISPLAYED_BOARDS) {
          break
        }

        val boardDescription = chanBoard.description

        val matches = query.isEmpty()
          || chanBoard.formattedBoardCode().contains(query, ignoreCase = true)
          || chanBoard.boardName().contains(query, ignoreCase = true)
          || (boardDescription.isEmpty() || boardDescription.contains(query, ignoreCase = true))

        if (matches) {
          matchedBoards += BoardForSelection(
            boardDescriptor = chanBoard.boardDescriptor,
            boardName = BoardHelper.formatName(chanBoard.boardDescriptor.boardCode, chanBoard.boardName()),
            description = BoardHelper.formatDescription(chanBoard)
          )
        }
      }

      val sortedBoards = if (query.isEmpty()) {
        matchedBoards.sortedBy { matchedBoard ->
          matchedBoard.boardDescriptor.boardCode
        }
      } else {
        InputWithQuerySorter.sort(
          input = matchedBoards,
          query = query,
          textSelector = { boardForSelection -> boardForSelection.boardDescriptor.boardCode }
        )
      }

      Snapshot.withMutableSnapshot {
        _uiState.value = AsyncData.Data(Unit)
        _currentSearchQuery.value = query
        _boardsForSelection.clear()
        _boardsForSelection.addAll(sortedBoards)
      }

      awaitFrame()
      _resetScrollEventFlow.emit(Unit)
    }
  }

  data class BoardForSelection(
    val boardDescriptor: BoardDescriptor,
    val boardName: String,
    val description: String
  ) {
    fun composeKey(): BoardDescriptor = boardDescriptor
  }

  class Exception(message: String) : ClientException(message)

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val boardManager: BoardManager,
  ) : ViewModelAssistedFactory<AddBoardsControllerViewModel> {
    override fun create(handle: SavedStateHandle): AddBoardsControllerViewModel {
      return AddBoardsControllerViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        boardManager = boardManager,
      )
    }
  }

  companion object {
    private const val TAG = "AddBoardsControllerV2ViewModel"
    const val MAX_DISPLAYED_BOARDS = 256
  }
}