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
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.compose.AsyncUiData
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
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

class AddBoardsControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
) : KurobaViewModel() {
  private val _allNoneActiveBoards = mutableListWithCap<ChanBoard>(initialCapacity = 1024)

  private val _checkedBoards = mutableStateSetOf<BoardDescriptor>()
  val checkedBoards: SnapshotStateSet<BoardDescriptor>
    get() = _checkedBoards

  private val _uiState = mutableStateOf<AsyncUiData<Unit>>(AsyncUiData.NotInitialized)
  val uiState: State<AsyncUiData<Unit>>
    get() = _uiState

  private val _processing = mutableStateOf(false)
  val processing: State<Boolean>
    get() = _processing

  private val _boardsForSelection = mutableStateListOf<BoardForSelection>()
  val boardsForSelection: SnapshotStateList<BoardForSelection>
    get() = _boardsForSelection

  private val _currentSearchQuery = mutableStateOf("")
  val currentSearchQuery: State<String>
    get() = _currentSearchQuery

  private val _totalBoardsCount = mutableIntStateOf(-1)
  val totalBoardsCount: IntState
    get() = _totalBoardsCount

  private val _totalMatchedBySearchQueryCount = mutableIntStateOf(-1)
  val totalMatchedBySearchQueryCount: IntState
    get() = _totalMatchedBySearchQueryCount

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
    _uiState.value = AsyncUiData.NotInitialized

    viewModelScope.launch(Dispatchers.Default) {
      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptorAndActive(_siteDescriptor)
      if (site == null) {
        _uiState.value = AsyncUiData.Error(Exception("No site found by descriptor: ${_siteDescriptor}"))
        return@launch
      }

      val isSiteActive = siteManager.isSiteActive(_siteDescriptor)
      if (!isSiteActive) {
        _uiState.value = AsyncUiData.Error(Exception("Site with descriptor ${_siteDescriptor} is not active!"))
        return@launch
      }

      _uiState.value = AsyncUiData.Loading

      loadInactiveBoards(_siteDescriptor)
      findBoardsForSelection()
    }
  }

  fun onSearchQueryUpdated(query: String) {
    if (query == _currentSearchQuery.value) {
      return
    }

    _searchQueryUpdateExecutor.post(timeout = 200L) { findBoardsForSelection(query) }
  }

  fun onBoardCheckStateChanged(boardDescriptor: BoardDescriptor, check: Boolean) {
    if (check) {
      _checkedBoards.add(boardDescriptor)
    } else {
      _checkedBoards.remove(boardDescriptor)
    }
  }

  fun toggleAll() {
    if (_checkedBoards.size == _allNoneActiveBoards.size) {
      _checkedBoards.clear()
      return
    }

    val allBoardsDescriptors = _allNoneActiveBoards
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
    _allNoneActiveBoards.clear()
    val totalBoardsCount = AtomicInt(0)

    boardManager.viewBoardsWhile(
      boardViewMode = BoardManager.BoardViewMode.All,
      siteDescriptor = siteDescriptor
    ) { chanBoard ->
      if (!chanBoard.active) {
        totalBoardsCount.incrementAndFetch()
        _allNoneActiveBoards.add(chanBoard)
      }

      return@viewBoardsWhile true
    }

    _totalBoardsCount.intValue = totalBoardsCount.load()
  }

  private suspend fun findBoardsForSelection(query: String = "") {
    try {
      _processing.value = true

      return withContext(Dispatchers.Default) {
        val matchedBoards = mutableListWithCap<BoardForSelection>(MAX_DISPLAYED_BOARDS)
        var totalMatched = 0

        for (chanBoard in _allNoneActiveBoards) {
          val boardDescription = chanBoard.description

          val matches = query.isEmpty()
            || chanBoard.formattedBoardCode().contains(query, ignoreCase = true)
            || chanBoard.boardName().contains(query, ignoreCase = true)
            || (boardDescription.isEmpty() || boardDescription.contains(query, ignoreCase = true))

          if (matches) {
            ++totalMatched

            if (matchedBoards.size < MAX_DISPLAYED_BOARDS) {
              matchedBoards += BoardForSelection(
                boardDescriptor = chanBoard.boardDescriptor,
                boardName = BoardHelper.formatName(chanBoard.boardDescriptor.boardCode, chanBoard.boardName()),
                description = BoardHelper.formatDescription(chanBoard)
              )
            }
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
            textSelector = { boardForSelection -> boardForSelection.boardName }
          )
        }

        Snapshot.withMutableSnapshot {
          _uiState.value = AsyncUiData.UiData(Unit)
          _currentSearchQuery.value = query
          _totalMatchedBySearchQueryCount.intValue = totalMatched
          _boardsForSelection.clear()
          _boardsForSelection.addAll(sortedBoards)
        }

        awaitFrame()
        _resetScrollEventFlow.emit(Unit)
      }
    } finally {
      _processing.value = false
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
    const val MAX_DISPLAYED_BOARDS = 256
  }
}