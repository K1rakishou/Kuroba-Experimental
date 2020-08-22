package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.base.SuspendDebouncer
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.common.mutableListWithCap
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.github.adamantcheese.model.repository.BoardRepository
import com.github.adamantcheese.model.repository.SiteRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class BoardManager(
  private val appScope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val siteRepository: SiteRepository,
  private val boardRepository: BoardRepository
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("BoardManager")
  private val persistBoardsDebouncer = SuspendDebouncer(appScope)

  private val currentBoardSubject = BehaviorProcessor.create<CurrentBoard>()
  private val boardsChangedSubject = PublishProcessor.create<Unit>()

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val boardsMap = mutableMapOf<SiteDescriptor, MutableMap<BoardDescriptor, ChanBoard>>()
  @GuardedBy("lock")
  private val ordersMap = mutableMapOf<SiteDescriptor, MutableList<BoardDescriptor>>()

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    appScope.launch(Dispatchers.Default) {
      val time = measureTime { loadBoardsInternal() }
      Logger.d(TAG, "loadBoards() took ${time}")
    }
  }

  private suspend fun loadBoardsInternal() {
    val loadBoardsResult = boardRepository.loadAllBoards()
    if (loadBoardsResult is ModularResult.Error) {
      Logger.e(TAG, "boardRepository.loadAllBoards() error", loadBoardsResult.error)
      suspendableInitializer.initWithError(loadBoardsResult.error)
      return
    }

    siteRepository.awaitUntilSitesLoaded()

    val loadSitesResult = siteRepository.loadAllSites()
    if (loadSitesResult is ModularResult.Error) {
      Logger.e(TAG, "siteRepository.loadAllSites() error", loadSitesResult.error)
      suspendableInitializer.initWithError(loadSitesResult.error)
      return
    }

    try {
      loadBoardsResult as ModularResult.Value
      loadSitesResult as ModularResult.Value

      lock.write {
        loadSitesResult.value.forEach { chanSiteData ->
          ordersMap[chanSiteData.siteDescriptor] = mutableListWithCap(64)
          boardsMap[chanSiteData.siteDescriptor] = mutableMapWithCap(64)
        }

        loadBoardsResult.value.forEach { (siteDescriptor, chanBoards) ->
          val sortedBoards = chanBoards.sortedByDescending { chanBoard -> chanBoard.order }

          sortedBoards.forEach { chanBoard ->
            boardsMap[siteDescriptor]!!.put(chanBoard.boardDescriptor, chanBoard)
            ordersMap[siteDescriptor]!!.add(chanBoard.boardDescriptor)
          }
        }
      }

      ensureBoardsAndOrdersConsistency()
      suspendableInitializer.initWithValue(Unit)
    } catch (error: Throwable) {
      Logger.e(TAG, "BoardManager initialization error", error)
      suspendableInitializer.initWithError(error)
    }
  }

  fun listenForSitesChanges(): Flowable<Unit> {
    return boardsChangedSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for sitesChangedSubject updates", error) }
      .hide()
  }

  fun listenForCurrentSelectedBoard(): Flowable<CurrentBoard> {
    return currentBoardSubject
      .onBackpressureLatest()
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for currentBoardSubject", error) }
      .hide()
  }

  suspend fun createOrUpdateBoards(boards: List<ChanBoard>): Boolean {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    val updated = lock.write {
      var updated = false

      boards.forEach { board ->
        val siteDescriptor = board.boardDescriptor.siteDescriptor

        val innerMap = boardsMap[siteDescriptor]
          ?: return@forEach

        var prevBoard = innerMap[board.boardDescriptor]
        if (prevBoard != null) {
          val newBoard = mergePrevAndNewBoards(prevBoard, board)
          if (prevBoard == newBoard) {
            return@forEach
          }

          prevBoard = newBoard
        } else {
          prevBoard = board
          ordersMap[siteDescriptor]?.add(board.boardDescriptor)
        }

        innerMap[board.boardDescriptor] = prevBoard
        updated = true
      }

      return@write updated
    }

    ensureBoardsAndOrdersConsistency()

    if (!updated) {
      return false
    }

    persistBoards()
    return true
  }

  suspend fun activateDeactivateBoard(boardDescriptor: BoardDescriptor, activate: Boolean): Boolean {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    val result = boardRepository.activateDeactivateBoard(boardDescriptor, activate)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "boardRepository.activateDeactivateBoard() error", result.error)
      return false
    }

    val updated = result.valueOrNull() ?: false
    if (!updated) {
      return false
    }

    val changed = lock.write {
      val innerMap = boardsMap[boardDescriptor.siteDescriptor]
        ?: return@write false

      val board = innerMap.get(boardDescriptor)
        ?: return@write false

      if (board.active == activate) {
        return@write false
      }

      board.active = activate
      return@write true
    }

    if (!changed) {
      return false
    }

    return true
  }

  fun firstBoardDescriptor(siteDescriptor: SiteDescriptor): BoardDescriptor? {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    return lock.read {
      val boardDescriptorsOrdered = ordersMap[siteDescriptor]
        ?: return@read null

      for (boardDescriptor in boardDescriptorsOrdered) {
        val board = boardsMap[siteDescriptor]?.get(boardDescriptor)
          ?: continue

        if (board.active) {
          return@read boardDescriptor
        }
      }

      return@read null
    }
  }

  fun updateCurrentBoard(boardDescriptor: BoardDescriptor?) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    if (currentBoardSubject.value?.boardDescriptor == boardDescriptor) {
      return
    }

    currentBoardSubject.onNext(CurrentBoard.create(boardDescriptor))
  }

  fun viewAllActiveBoards(func: (ChanBoard) -> Unit) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    lock.read {
      boardsMap.values.forEach { innerMap ->
        innerMap.values.forEach { chanBoard ->
          if (chanBoard.active) {
            func(chanBoard)
          }
        }
      }
    }
  }

  fun viewActiveBoardsOrdered(siteDescriptor: SiteDescriptor, func: (ChanBoard) -> Unit) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    lock.read {
      ordersMap[siteDescriptor]?.forEach { boardDescriptor ->
        val chanBoard = boardsMap[siteDescriptor]?.get(boardDescriptor)
          ?: return@forEach

        if (chanBoard.active) {
          func(chanBoard)
        }
      }
    }
  }

  fun byBoardDescriptor(boardDescriptor: BoardDescriptor): ChanBoard? {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    return lock.read { boardsMap[boardDescriptor.siteDescriptor]?.get(boardDescriptor) }
  }

  fun activeBoardsCount(siteDescriptor: SiteDescriptor): Int {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    return lock.read {
      return@read boardsMap[siteDescriptor]?.entries?.count { (boardDescriptor, board) ->
        board.active && boardDescriptor.siteDescriptor == siteDescriptor
      } ?: 0
    }
  }

  fun onBoardMoved(boardDescriptor: BoardDescriptor, from: Int, to: Int): Boolean {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    val moved = lock.write {
      val orders = ordersMap[boardDescriptor.siteDescriptor]
        ?: return@write false

      if (orders.get(from) != boardDescriptor) {
        return@write false
      }

      orders.add(to, orders.removeAt(from))
      return@write true
    }

    if (!moved) {
      return false
    }

    persistBoardsDebouncer.post(DEBOUNCE_TIME_MS) { persistBoards() }
    sitesChanged()

    return true
  }

  fun onBoardRemoved(boardDescriptor: BoardDescriptor): Boolean {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    val removed = lock.write {
      var contains = boardsMap.containsKey(boardDescriptor.siteDescriptor)
      if (contains) {
        val removed = boardsMap[boardDescriptor.siteDescriptor]!!.remove(boardDescriptor) != null
        if (!removed) {
          return@write false
        }
      }

      contains = ordersMap.containsKey(boardDescriptor.siteDescriptor)
      if (contains) {
        val removed = ordersMap[boardDescriptor.siteDescriptor]!!.remove(boardDescriptor)
        if (!removed) {
          return@write false
        }
      }

      return@write true
    }

    if (!removed) {
      return false
    }

    persistBoardsDebouncer.post(DEBOUNCE_TIME_MS) { persistBoards() }
    sitesChanged()

    return true
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "BoardManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "BoardManager initialization completed, took $duration")
  }

  private suspend fun persistBoards() {
    val result = boardRepository.persist(getBoardsOrdered())
    if (result is ModularResult.Error) {
      Logger.e(TAG, "boardRepository.persist() error", result.error)
      return
    }
  }

  private fun ensureBoardsAndOrdersConsistency() {
    if (isDevFlavor) {
      lock.read {
        check(boardsMap.size == ordersMap.size) {
          "Inconsistency detected! boardsMap.size (${boardsMap.size}) != orders.size (${ordersMap.size})"
        }

        boardsMap.forEach { (siteDescriptor, innerMap) ->
          val innerBoardsMapCount = innerMap.count()
          val innerOrdersMapCount = ordersMap[siteDescriptor]?.count() ?: 0

          check(innerBoardsMapCount == innerOrdersMapCount) {
            "Inconsistency detected! innerBoardsMapCount (${innerBoardsMapCount}) != " +
              "innerOrdersMapCount (${innerOrdersMapCount})"
          }
        }
      }
    }
  }

  private fun getBoardsOrdered(): Map<SiteDescriptor, List<ChanBoard>> {
    ensureBoardsAndOrdersConsistency()
    val resultMap = mutableMapOf<SiteDescriptor, MutableList<ChanBoard>>()

    lock.read {
      ordersMap.forEach { (siteDescriptor, boardDescriptorsOrdered) ->
        if (!resultMap.containsKey(siteDescriptor)) {
          resultMap.put(siteDescriptor, mutableListWithCap(boardDescriptorsOrdered))
        }

        boardDescriptorsOrdered.forEach { boardDescriptor ->
          val board = boardsMap[siteDescriptor]?.get(boardDescriptor)
            ?: return@forEach

          resultMap[siteDescriptor]!!.add(board)
        }

      }
    }

    return resultMap
  }

  private fun sitesChanged() {
    if (isDevFlavor) {
      ensureBoardsAndOrdersConsistency()
    }

    boardsChangedSubject.onNext(Unit)
  }

  private fun isReady() = suspendableInitializer.isInitialized()

  private fun mergePrevAndNewBoards(prevBoard: ChanBoard, newBoard: ChanBoard): ChanBoard {
    return ChanBoard(
      boardDescriptor = prevBoard.boardDescriptor,
      active = prevBoard.active,
      order = prevBoard.order,
      name = newBoard.name,
      perPage = newBoard.perPage,
      pages = newBoard.pages,
      maxFileSize = newBoard.maxFileSize,
      maxWebmSize = newBoard.maxWebmSize,
      maxCommentChars = newBoard.maxCommentChars,
      bumpLimit = newBoard.bumpLimit,
      imageLimit = newBoard.imageLimit,
      cooldownThreads = newBoard.cooldownThreads,
      cooldownReplies = newBoard.cooldownReplies,
      cooldownImages = newBoard.cooldownImages,
      customSpoilers = newBoard.customSpoilers,
      description = newBoard.description,
      saved = newBoard.saved,
      workSafe = newBoard.workSafe,
      spoilers = newBoard.spoilers,
      userIds = newBoard.userIds,
      codeTags = newBoard.codeTags,
      preuploadCaptcha = newBoard.preuploadCaptcha,
      countryFlags = newBoard.countryFlags,
      mathTags = newBoard.mathTags,
      archive = newBoard.archive,
    )
  }

  sealed class CurrentBoard(val boardDescriptor: BoardDescriptor?) {
    object Empty : CurrentBoard(null)
    class Board(boardDescriptor: BoardDescriptor) : CurrentBoard(boardDescriptor)

    companion object {
      fun create(boardDescriptor: BoardDescriptor?): CurrentBoard {
        if (boardDescriptor == null) {
          return Empty
        }

        return Board(boardDescriptor)
      }
    }
  }

  companion object {
    private const val TAG = "BoardManager"
    private const val DEBOUNCE_TIME_MS = 500L
  }
}