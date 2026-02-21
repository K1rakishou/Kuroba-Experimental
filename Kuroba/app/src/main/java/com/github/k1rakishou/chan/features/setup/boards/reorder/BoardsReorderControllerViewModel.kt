package com.github.k1rakishou.chan.features.setup.boards.reorder

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.concurrency.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.ui.compose.reorder.move
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.chan.utils.requireParams
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BoardsReorderControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val appResources: AppResources
) : KurobaViewModel() {
  private val _params = savedStateHandle.requireParams<BoardsReorderControllerParams>()

  val siteDescriptor: SiteDescriptor
    get() = _params.siteDescriptor

  private val _error = mutableStateOf<Throwable?>(null)
  val error: State<Throwable?>
    get() = _error

  private val _loading = mutableStateOf(false)
  val loading: State<Boolean>
    get() = _loading

  private val _reorderableBoards = mutableStateListOf<ReorderableBoard>()
  val reorderableBoards: SnapshotStateList<ReorderableBoard>
    get() = _reorderableBoards

  private val _selectedBoards = mutableStateSetOf<BoardDescriptor>()
  val selectedBoards: SnapshotStateSet<BoardDescriptor>
    get() = _selectedBoards

  private val _suspendDebouncer = DebouncingCoroutineExecutor(viewModelScope)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    viewModelScope.launch {
      siteManager.awaitUntilInitialized()
      boardManager.awaitUntilInitialized()

      val siteIsSynthetic = siteManager.bySiteDescriptorAndActive(siteDescriptor)?.isSynthetic
        ?: false

      val boardsCount = boardManager.boardsCount(siteDescriptor)
      if (siteIsSynthetic || boardsCount > 0) {
        displayActiveBoardsInternal()
      } else {
        updateBoardsFromServerAndDisplayActive()
      }
    }
  }

  fun isSyntheticSite(): Boolean {
    val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)!!
    return site.isSynthetic
  }

  fun updateBoardsFromServerAndDisplayActive() {
    viewModelScope.launch(Dispatchers.Default) {
      _error.value = null
      _loading.value = true

      try {
        boardManager.awaitUntilInitialized()
        siteManager.awaitUntilInitialized()

        val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
        if (site == null) {
          _error.value = Exception("No sites found by descriptor: ${siteDescriptor}")
          return@launch
        }

        if (site.siteFeature(Site.SiteFeature.CATALOG_COMPOSITION)) {
          displayActiveBoardsInternal()
          return@launch
        }

        val isSiteActive = siteManager.isSiteActive(siteDescriptor)
        if (!isSiteActive) {
          _error.value = Exception("Site with descriptor ${siteDescriptor} is not active!")
          return@launch
        }

        val loadedBoardsCount = site.loadBoardInfo()
          .mapValue { siteBoards -> siteBoards.boards.size }
          .safeUnwrap { error ->
            Logger.e(TAG, "Error loading boards for site ${siteDescriptor}", error)
            _error.value = Exception(error.errorMessageOrClassName())
            return@launch
          }

        displayActiveBoardsInternal()

        controllerDelegate.toast(
          message = appResources.string(R.string.controller_boards_reorder_n_boards_loaded, loadedBoardsCount)
        )
      } finally {
        _loading.value = false
      }
    }
  }

  fun moveBoard(fromBoardDescriptor: BoardDescriptor, toBoardDescriptor: BoardDescriptor) {
    if (!boardManager.onBoardMoving(fromBoardDescriptor, toBoardDescriptor)) {
      return
    }

    Snapshot.withMutableSnapshot {
      val fromIndex = _reorderableBoards
        .indexOfFirst { reorderableBoard -> reorderableBoard.boardDescriptor == fromBoardDescriptor }
      val toIndex = _reorderableBoards
        .indexOfFirst { reorderableBoard -> reorderableBoard.boardDescriptor == toBoardDescriptor }

      if (fromIndex == toIndex) {
        return@withMutableSnapshot
      }

      if (fromIndex < 0 || toIndex < 0) {
        return@withMutableSnapshot
      }

      _reorderableBoards.move(fromIndex, toIndex)
    }
  }

  fun moveBoardEnd() {
    boardManager.onBoardMoved()
  }

  fun onDeleteBoardsClicked() {
    viewModelScope.launch {
      val boardsToDelete = _selectedBoards.toSet()
      if (boardsToDelete.isEmpty()) {
        return@launch
      }

      val siteDescriptor = boardsToDelete.first().siteDescriptor

      val deactivated = boardManager.activateDeactivateBoards(
        siteDescriptor = siteDescriptor,
        boardDescriptors = boardsToDelete,
        activate = false
      )

      if (deactivated) {
        _selectedBoards.clear()
        displayActiveBoards(withLoadingState = false, withDebouncing = true)

        controllerDelegate.toast(
          message = appResources.string(R.string.controller_boards_reorder_deleted_n_boards, boardsToDelete.size)
        )
      }
    }
  }

  fun onBoardSelectionChanged(boardDescriptor: BoardDescriptor, nowSelected: Boolean) {
    if (nowSelected) {
      _selectedBoards.add(boardDescriptor)
    } else {
      _selectedBoards.remove(boardDescriptor)
    }
  }

  fun unselectAll() {
    _selectedBoards.clear()
  }

  fun toggleSelectionForAllBoards() {
    if (_selectedBoards.size >= _reorderableBoards.size) {
      _selectedBoards.clear()
    } else {
      _selectedBoards.clear()
      _selectedBoards.addAll(_reorderableBoards.map { it.boardDescriptor })
    }
  }

  fun isReorderableBoardSelected(boardDescriptor: BoardDescriptor): Boolean {
    return _selectedBoards.contains(boardDescriptor)
  }

  fun displayActiveBoards(withLoadingState: Boolean = true, withDebouncing: Boolean = true) {
    if (withLoadingState) {
      _loading.value = true
    }

    if (withDebouncing) {
      _suspendDebouncer.post(DEBOUNCE_TIME_MS) {
        boardManager.awaitUntilInitialized()
        siteManager.awaitUntilInitialized()

        displayActiveBoardsInternal()
      }
    } else {
      viewModelScope.launch {
        boardManager.awaitUntilInitialized()
        siteManager.awaitUntilInitialized()

        displayActiveBoardsInternal()
      }
    }
  }

  fun sortBoardsAlphabetically() {
    val activeBoards = mutableListOf<BoardDescriptor>()

    boardManager.viewBoardsWhile(
      boardViewMode = BoardManager.BoardViewMode.Active,
      siteDescriptor = siteDescriptor
    ) { chanBoard ->
      activeBoards += chanBoard.boardDescriptor
      return@viewBoardsWhile true
    }

    activeBoards
      .sortBy { boardDescriptor -> boardDescriptor.boardCode }

    boardManager.reorder(siteDescriptor, activeBoards)
    displayActiveBoards(withLoadingState = false, withDebouncing = true)
  }

  fun deactivateAllBoards() {
    viewModelScope.launch {
      val boardsToDeactivate = mutableSetOf<BoardDescriptor>()

      boardManager.viewBoardsWhile(
        boardViewMode = BoardManager.BoardViewMode.Active,
        siteDescriptor = siteDescriptor
      ) { chanBoard ->
        boardsToDeactivate += chanBoard.boardDescriptor
        return@viewBoardsWhile true
      }

      if (boardsToDeactivate.isEmpty()) {
        return@launch
      }

      boardManager.activateDeactivateBoards(
        siteDescriptor = siteDescriptor,
        boardDescriptors = boardsToDeactivate,
        activate = false
      )

      displayActiveBoardsInternal()
    }
  }

  fun onBackPressed(): Boolean {
    if (_selectedBoards.isNotEmpty()) {
      _selectedBoards.clear()
      return true
    }

    return false
  }


  private suspend fun displayActiveBoardsInternal() {
    try {
      val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
      if (site == null) {
        _error.value = Exception("Site with descriptor ${siteDescriptor} does not exist!")
        return
      }

      val isSiteActive = siteManager.isSiteActive(siteDescriptor)
      if (!isSiteActive) {
        _error.value = Exception("Site with descriptor ${siteDescriptor} is not active!")
        return
      }

      if (site.siteFeature(Site.SiteFeature.CATALOG_COMPOSITION)) {
        error("Cannot use sites with 'CATALOG_COMPOSITION' feature here")
      }

      val reorderableBoards = withContext(Dispatchers.Default) {
        val reorderableBoards = mutableListWithCap<ReorderableBoard>(32)

        boardManager.viewBoardsOrdered(
          siteDescriptor = siteDescriptor,
          boardViewMode = BoardManager.BoardViewMode.Active
        ) { chanBoard ->
          reorderableBoards += ReorderableBoard(
            boardDescriptor = chanBoard.boardDescriptor,
            boardName = chanBoard.boardName(),
            description = BoardHelper.formatDescription(chanBoard)
          )

          return@viewBoardsOrdered true
        }

        return@withContext reorderableBoards
      }

      _reorderableBoards.clear()
      _reorderableBoards.addAll(reorderableBoards)
    } finally {
      _loading.value = false
    }
  }

  data class ReorderableBoard(
    val boardDescriptor: BoardDescriptor,
    val boardName: String,
    val description: String
  )

  class Exception(message: String) : ClientException(message)

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val boardManager: BoardManager,
    private val appResources: AppResources
  ) : ViewModelAssistedFactory<BoardsReorderControllerViewModel> {
    override fun create(handle: SavedStateHandle): BoardsReorderControllerViewModel {
      return BoardsReorderControllerViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        boardManager = boardManager,
        appResources = appResources
      )
    }
  }

  companion object {
    private const val TAG = "BoardsReorderControllerViewModel"
    private const val DEBOUNCE_TIME_MS = 100L
  }
}