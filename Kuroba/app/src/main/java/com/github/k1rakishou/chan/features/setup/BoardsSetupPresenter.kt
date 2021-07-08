package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.usecase.CreateBoardManuallyUseCase
import com.github.k1rakishou.chan.features.setup.data.BoardCellData
import com.github.k1rakishou.chan.features.setup.data.BoardsSetupControllerState
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

class BoardsSetupPresenter(
  private val siteDescriptor: SiteDescriptor,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val createBoardManuallyUseCase: CreateBoardManuallyUseCase
) : BasePresenter<BoardsSetupView>() {
  private val suspendDebouncer = DebouncingCoroutineExecutor(scope)
  private val stateSubject = PublishProcessor.create<BoardsSetupControllerState>()
    .toSerialized()

  private val boardInfoLoaded = AtomicBoolean(false)

  fun listenForStateChanges(): Flowable<BoardsSetupControllerState> {
    return stateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to stateSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> BoardsSetupControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  fun createBoardManually(boardCode: String) {
    scope.launch(Dispatchers.Default) {
      withView { showLoadingView() }

      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      val boardDescriptor = BoardDescriptor.create(siteDescriptor, boardCode)
      val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor)

      if (boardManager.byBoardDescriptor(boardDescriptor) != null) {
        withView {
          showMessageToast("Board /$boardCode/ already exists! You need to add it via AddBoards menu.")
          hideLoadingView()
        }
        return@launch
      }

      when (val result = createBoardManuallyUseCase.execute(catalogDescriptor)) {
        is CreateBoardManuallyUseCase.Result.FailedToFindSite -> {
          val errorMessage = "Failed to find site: ${result.siteDescriptor.siteName}"

          withView {
            Logger.e(TAG, errorMessage)
            showMessageToast(errorMessage)
          }
        }
        is CreateBoardManuallyUseCase.Result.BadResponse -> {
          val errorMessage = "Bad server response code: ${result.code}"

          withView {
            Logger.e(TAG, errorMessage)
            showMessageToast(errorMessage)
          }
        }
        is CreateBoardManuallyUseCase.Result.UnknownError -> {
          withView {
            Logger.e(TAG, "createBoardManuallyUseCase.execute($catalogDescriptor) error", result.throwable)

            showMessageToast("createBoardManuallyUseCase.execute($catalogDescriptor), " +
              "error=${result.throwable.errorMessageOrClassName()}")
          }
        }
        is CreateBoardManuallyUseCase.Result.FailedToParseAnyThreads -> {
          val errorMessage = "Failed to parse any threads, most likely " +
            "the board '/${result.chanDescriptor.boardCode()}/' does not exists"

          withView {
            Logger.e(TAG, errorMessage)
            showMessageToast(errorMessage)
          }
        }
        is CreateBoardManuallyUseCase.Result.Success -> {
          Logger.d(TAG, "createBoardManuallyUseCase.execute($catalogDescriptor) success")

          addNewBoardAndActivate(boardDescriptor)
        }
      }

      withView { hideLoadingView() }
    }
  }

  private suspend fun addNewBoardAndActivate(boardDescriptor: BoardDescriptor) {
    val newBoard = ChanBoard.create(boardDescriptor, null)

    if (!boardManager.createOrUpdateBoards(listOf(newBoard))) {
      val errorMessage = "Failed to create board ${newBoard} in the database"

      withView {
        Logger.e(TAG, errorMessage)
        showMessageToast(errorMessage)
      }

      return
    }

    val activateResult = boardManager.activateDeactivateBoards(
      siteDescriptor = boardDescriptor.siteDescriptor,
      boardDescriptors = linkedSetOf(boardDescriptor),
      activate = true
    )

    if (!activateResult) {
      val errorMessage = "Failed to activate board ${newBoard}"

      withView {
        Logger.e(TAG, errorMessage)
        showMessageToast(errorMessage)
      }

      return
    }

    withView {
      val boardCode = boardDescriptor.boardCode

      Logger.d(TAG, "addNewBoardAndActivate($boardDescriptor) success")
      showMessageToast("Successfully created board /${boardCode}/")
    }
  }

  fun updateBoardsFromServerAndDisplayActive() {
    scope.launch(Dispatchers.Default) {
      setState(BoardsSetupControllerState.Loading)

      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptor(siteDescriptor)
      if (site == null) {
        setState(BoardsSetupControllerState.Error("No site found by descriptor: ${siteDescriptor}"))
        boardInfoLoaded.set(true)
        return@launch
      }

      val isSiteActive = siteManager.isSiteActive(siteDescriptor)
      if (!isSiteActive) {
        setState(BoardsSetupControllerState.Error("Site with descriptor ${siteDescriptor} is not active!"))
        boardInfoLoaded.set(true)
        return@launch
      }

      loadBoardInfoSuspend(site)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error loading boards for site ${siteDescriptor}", error)
          setState(BoardsSetupControllerState.Error(error.errorMessageOrClassName()))
          boardInfoLoaded.set(true)
          return@launch
        }

      withViewNormal { onBoardsLoaded() }

      displayActiveBoardsInternal()
      boardInfoLoaded.set(true)
    }
  }

  fun onBoardMoving(boardDescriptor: BoardDescriptor, fromPosition: Int, toPosition: Int) {
    if (boardManager.onBoardMoving(boardDescriptor, fromPosition, toPosition)) {
      displayActiveBoards(withLoadingState = false, withDebouncing = false)
    }
  }

  fun onBoardMoved() {
    boardManager.onBoardMoved()
  }

  fun onBoardRemoved(boardDescriptor: BoardDescriptor) {
    scope.launch {
      val deactivated = boardManager.activateDeactivateBoards(
        boardDescriptor.siteDescriptor,
        linkedSetOf(boardDescriptor),
        false
      )

      if (deactivated) {
        displayActiveBoards(withLoadingState = false, withDebouncing = true)
      }
    }
  }

  fun displayActiveBoards(withLoadingState: Boolean = true, withDebouncing: Boolean = true) {
    if (!boardInfoLoaded.get()) {
      return
    }

    if (withLoadingState) {
      setState(BoardsSetupControllerState.Loading)
    }

    if (withDebouncing) {
      suspendDebouncer.post(DEBOUNCE_TIME_MS) {
        boardManager.awaitUntilInitialized()
        siteManager.awaitUntilInitialized()

        displayActiveBoardsInternal()
      }
    } else {
      scope.launch {
        boardManager.awaitUntilInitialized()
        siteManager.awaitUntilInitialized()

        displayActiveBoardsInternal()
      }
    }
  }

  fun sortBoardsAlphabetically() {
    val activeBoards = mutableListOf<BoardDescriptor>()
    boardManager.viewAllBoards(siteDescriptor) { chanBoard ->
      if (chanBoard.active) {
        activeBoards += chanBoard.boardDescriptor
      }
    }

    activeBoards
      .sortBy { boardDescriptor -> boardDescriptor.boardCode }

    boardManager.reorder(siteDescriptor, activeBoards)
    displayActiveBoards(withLoadingState = false, withDebouncing = true)
  }

  private fun displayActiveBoardsInternal() {
    val isSiteActive = siteManager.isSiteActive(siteDescriptor)
    if (!isSiteActive) {
      setState(BoardsSetupControllerState.Error("Site with descriptor ${siteDescriptor} is not active!"))
      return
    }

    val boardCellDataList = mutableListWithCap<BoardCellData>(32)

    boardManager.viewBoardsOrdered(siteDescriptor, true) { chanBoard ->
      boardCellDataList += BoardCellData(
        searchQuery = null,
        boardDescriptor = chanBoard.boardDescriptor,
        boardName = chanBoard.boardName(),
        description = BoardHelper.getDescription(chanBoard)
      )
    }

    if (boardCellDataList.isEmpty()) {
      setState(BoardsSetupControllerState.Empty)
      return
    }

    setState(BoardsSetupControllerState.Data(boardCellDataList))
  }

  private suspend fun loadBoardInfoSuspend(site: Site): ModularResult<Unit> {
    if (boardInfoLoaded.get()) {
      return ModularResult.value(Unit)
    }

    return suspendCancellableCoroutine { cancellableContinuation ->
      site.loadBoardInfo { result ->
        cancellableContinuation.resumeValueSafe(result.mapValue { Unit })
      }
    }
  }

  private fun setState(state: BoardsSetupControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardsSetupPresenter"
    private const val DEBOUNCE_TIME_MS = 100L
  }
}