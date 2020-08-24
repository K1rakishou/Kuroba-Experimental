package com.github.adamantcheese.chan.features.setup

import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.base.SuspendDebouncer
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.features.setup.data.BoardCellData
import com.github.adamantcheese.chan.features.setup.data.BoardsSetupControllerState
import com.github.adamantcheese.chan.ui.helper.BoardHelper
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.errorMessageOrClassName
import com.github.adamantcheese.common.mutableListWithCap
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume

class BoardsSetupPresenter(
  private val siteDescriptor: SiteDescriptor
) : BasePresenter<BoardsSetupView>() {
  private val suspendDebouncer = SuspendDebouncer(scope)
  private val stateSubject = PublishProcessor.create<BoardsSetupControllerState>()
    .toSerialized()

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager

  private val boardInfoLoaded = AtomicBoolean(false)

  override fun onCreate(view: BoardsSetupView) {
    super.onCreate(view)
    Chan.inject(this)
  }

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
          return@launch
        }

      displayActiveBoardsInternal()
      boardInfoLoaded.set(true)
    }
  }

  fun displayActiveBoards() {
    if (!boardInfoLoaded.get()) {
      return
    }

    setState(BoardsSetupControllerState.Loading)

    suspendDebouncer.post(DEBOUNCE_TIME_MS) {
      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      displayActiveBoardsInternal()
    }
  }

  fun onBoardMoved(boardDescriptor: BoardDescriptor, fromPosition: Int, toPosition: Int) {
    if (boardManager.onBoardMoved(boardDescriptor, fromPosition, toPosition)) {
      displayActiveBoards()
    }
  }

  fun onBoardRemoved(boardDescriptor: BoardDescriptor) {
    if (boardManager.onBoardRemoved(boardDescriptor)) {
      displayActiveBoards()
    }
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
        chanBoard.boardDescriptor,
        chanBoard.boardName(),
        BoardHelper.getDescription(chanBoard)
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
        cancellableContinuation.resume(result.mapValue { Unit })
      }
    }
  }

  private fun setState(state: BoardsSetupControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardsSetupPresenter"
    private const val DEBOUNCE_TIME_MS = 500L
  }
}