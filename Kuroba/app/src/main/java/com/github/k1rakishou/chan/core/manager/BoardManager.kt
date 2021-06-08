package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.model.repository.BoardRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@DoNotStrip
class BoardManager(
  private val appScope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val boardRepository: BoardRepository
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("BoardManager")
  private val persistBoardsDebouncer = DebouncingCoroutineExecutor(appScope)

  private val currentBoardSubject = BehaviorProcessor.create<CurrentBoard>()
  private val boardsChangedSubject = PublishProcessor.create<Unit>()
  private val boardMovedSubject = PublishProcessor.create<Unit>()

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val boardsMap = mutableMapOf<SiteDescriptor, MutableMap<BoardDescriptor, ChanBoard>>()
  @GuardedBy("lock")
  private val ordersMap = mutableMapOf<SiteDescriptor, MutableList<BoardDescriptor>>()

  @OptIn(ExperimentalTime::class)
  fun initialize(siteDataListAsync: CompletableDeferred<List<ChanSiteData>>) {
    Logger.d(TAG, "BoardManager.initialize()")

    appScope.launch(Dispatchers.IO) {
      Logger.d(TAG, "loadBoardsInternal() start")
      val time = measureTime { loadBoardsInternal(siteDataListAsync) }
      Logger.d(TAG, "loadBoardsInternal() took ${time}")
    }
  }

  private suspend fun loadBoardsInternal(siteDataListAsync: CompletableDeferred<List<ChanSiteData>>) {
    try {
      Logger.d(TAG, "loadBoardsInternal() siteDataListAsync.get() start")
      val allLoadedSites = siteDataListAsync.await()
      Logger.d(TAG, "loadBoardsInternal() siteDataListAsync.get() end")

      val loadBoardsResult = boardRepository.loadAllBoards()
      if (loadBoardsResult is ModularResult.Error) {
        Logger.e(TAG, "boardRepository.loadAllBoards() error", loadBoardsResult.error)
        suspendableInitializer.initWithError(loadBoardsResult.error)
        return
      }

      loadBoardsResult as ModularResult.Value

      lock.write {
        boardsMap.clear()
        ordersMap.clear()

        allLoadedSites.forEach { chanSiteData ->
          ordersMap[chanSiteData.siteDescriptor] = mutableListWithCap(64)
          boardsMap[chanSiteData.siteDescriptor] = linkedMapWithCap(64)
        }

        loadBoardsResult.value.forEach { (siteDescriptor, chanBoards) ->
          val siteBoardMap = boardsMap[siteDescriptor]
          if (siteBoardMap == null) {
            Logger.e(TAG, "boardsMap has no site: ${siteDescriptor}")
            return@forEach
          }

          val siteOrderList = ordersMap[siteDescriptor]
          if (siteOrderList == null) {
            Logger.e(TAG, "ordersMap has no site: ${siteDescriptor}")
            return@forEach
          }

          chanBoards.forEach { chanBoard ->
            siteBoardMap[chanBoard.boardDescriptor] = chanBoard
          }

          val sortedActiveBoards = chanBoards
            .filter { chanBoard -> chanBoard.active }
            .sortedBy { chanBoard -> chanBoard.order }

          sortedActiveBoards.forEach { activeBoard ->
            siteOrderList.add(activeBoard.boardDescriptor)
          }
        }
      }

      ensureBoardsAndOrdersConsistency()
      suspendableInitializer.initWithValue(Unit)

      val totalLoadedBoards = loadBoardsResult.value.values.sumBy { siteBoards -> siteBoards.size }
      Logger.d(TAG, "loadBoardsInternal() done. Loaded ${totalLoadedBoards} boards")
    } catch (error: Throwable) {
      suspendableInitializer.initWithError(error)
      Logger.e(TAG, "loadBoardsInternal() error", error)
    }
  }

  fun listenForSitesChanges(): Flowable<Unit> {
    return boardsChangedSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for sitesChangedSubject updates", error) }
      .hide()
  }

  fun listenForBoardsMoves(): Flowable<Unit> {
    return boardMovedSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for boardMovedSubject updates", error) }
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

  fun currentBoardDescriptor(): BoardDescriptor? = currentBoardSubject.value?.boardDescriptor

  suspend fun createOrUpdateBoards(boards: List<ChanBoard>): Boolean {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    val updated = synchronized(this) {
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

          if (board.active) {
            ordersMap[siteDescriptor]?.add(board.boardDescriptor)
          }
        }

        innerMap[board.boardDescriptor] = prevBoard
        updated = true
      }

      return@synchronized updated
    }

    ensureBoardsAndOrdersConsistency()

    if (!updated) {
      return false
    }

    val siteDescriptors = boards
      .map { chanBoard -> chanBoard.boardDescriptor.siteDescriptor }
      .distinct()

    persistBoards(siteDescriptors)
    boardsChanged()

    return true
  }

  suspend fun activateDeactivateBoards(
    siteDescriptor: SiteDescriptor,
    boardDescriptors: Collection<BoardDescriptor>,
    activate: Boolean
  ): Boolean {
    if (boardDescriptors.isEmpty()) {
      return false
    }

    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    // Very bad, but whatever we only do this in one place where it's not critical
    val result = boardRepository.activateDeactivateBoards(
      siteDescriptor,
      boardDescriptors,
      activate
    )

    val changed = lock.write {
      if (result is ModularResult.Error) {
        Logger.e(TAG, "boardRepository.activateDeactivateBoard() error", result.error)
        return@write false
      }

      val updated = result.valueOrNull() ?: false
      if (!updated) {
        return@write false
      }

      var changed = false

      boardDescriptors.forEach { boardDescriptor ->
        val innerMap = boardsMap[boardDescriptor.siteDescriptor]
          ?: return@forEach

        val board = innerMap[boardDescriptor]
          ?: return@forEach

        if (board.active == activate) {
          return@forEach
        }

        ordersMap.putIfNotContains(boardDescriptor.siteDescriptor, mutableListOf())

        if (activate) {
          ordersMap[boardDescriptor.siteDescriptor]!!.add(boardDescriptor)
        } else {
          ordersMap[boardDescriptor.siteDescriptor]!!.remove(boardDescriptor)
        }

        board.active = activate
        changed = true
      }

      return@write changed
    }

    if (!changed) {
      boardRepository.activateDeactivateBoards(
        siteDescriptor,
        boardDescriptors,
        activate.not()
      )

      return false
    }

    persistBoards()

    when (val currentBoard = currentBoardSubject.value) {
      null,
      CurrentBoard.Empty -> {
        if (activate) {
          currentBoardSubject.onNext(CurrentBoard.create(boardDescriptors.first()))
        }
      }
      is CurrentBoard.Board -> {
        if (currentBoard.boardDescriptor in boardDescriptors && !activate) {
          val firstActiveBoard = firstBoardDescriptor(siteDescriptor)
          if (firstActiveBoard != null) {
            currentBoardSubject.onNext(CurrentBoard.create(firstActiveBoard))
          } else {
            currentBoardSubject.onNext(CurrentBoard.Empty)
          }
        }
      }
    }

    boardsChanged()

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

  fun viewBoards(siteDescriptor: SiteDescriptor, boardViewMode: BoardViewMode, func: (ChanBoard) -> Unit) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    lock.read {
      boardsMap[siteDescriptor]?.forEach { (_, chanBoard) ->
        when (boardViewMode) {
          BoardViewMode.AllBoards -> func(chanBoard)
          BoardViewMode.OnlyActiveBoards -> {
            if (chanBoard.active) {
              func(chanBoard)
            }
          }
          BoardViewMode.OnlyNonActiveBoards -> {
            if (!chanBoard.active) {
              func(chanBoard)
            }
          }
        }
      }
    }
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

  fun viewBoardsOrdered(siteDescriptor: SiteDescriptor, onlyActive: Boolean, func: (ChanBoard) -> Unit) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    lock.read {
      ordersMap[siteDescriptor]?.forEach { boardDescriptor ->
        val chanBoard = boardsMap[siteDescriptor]?.get(boardDescriptor)
          ?: return@forEach

        if (!onlyActive || chanBoard.active) {
          func(chanBoard)
        }
      }
    }
  }

  fun viewAllBoards(siteDescriptor: SiteDescriptor, func: (ChanBoard) -> Unit) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    lock.read {
      val boards = boardsMap[siteDescriptor]
        ?: return@read

      boards.values.forEach { chanBoard ->
        func(chanBoard)
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

  fun activeBoardsCountForAllSites(): Int {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    return lock.read {
      var activeBoardsCount = 0

      boardsMap.entries.forEach { (_, boardsMap) ->
        boardsMap.entries.forEach { (_, chanBoard) ->
          if (chanBoard.active) {
            ++activeBoardsCount
          }
        }
      }

      return@read activeBoardsCount
    }
  }

  fun getTotalCount(onlyActive: Boolean = false): Int {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    return lock.read {
      boardsMap.values.sumBy { innerMap ->
        if (onlyActive) {
          return@sumBy innerMap.values.count { chanBoard -> chanBoard.active }
        } else {
          return@sumBy innerMap.size
        }
      }
    }
  }

  fun onBoardMoving(boardDescriptor: BoardDescriptor, from: Int, to: Int): Boolean {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    val moved = lock.write {
      val orders = ordersMap[boardDescriptor.siteDescriptor]
        ?: return@write false

      if (orders[from] != boardDescriptor) {
        return@write false
      }

      orders.add(to, orders.removeAt(from))
      return@write true
    }

    if (!moved) {
      return false
    }

    return true
  }

  fun onBoardMoved() {
    persistBoardsDebouncer.post(BOARD_MOVED_DEBOUNCE_TIME_MS) { persistBoards() }
    boardsChanged()
  }

  fun getAllBoardDescriptorsForSite(siteDescriptor: SiteDescriptor): Set<BoardDescriptor> {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    return lock.read { boardsMap[siteDescriptor]?.keys ?: emptySet() }
  }

  fun getAllActiveBoardDescriptors(): Set<BoardDescriptor> {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    return lock.read {
      val allBoardDescriptors = mutableSetOf<BoardDescriptor>()

      boardsMap.entries.forEach { (_, boardsMap) ->
        boardsMap.entries.forEach { (_, chanBoard) ->
          if (chanBoard.active) {
            allBoardDescriptors += chanBoard.boardDescriptor
          }
        }
      }

      return@read allBoardDescriptors
    }
  }

  fun isReady() = suspendableInitializer.isInitialized()

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "BoardManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "BoardManager initialization completed, took $duration")
  }

  private suspend fun persistBoards(siteDescriptors: List<SiteDescriptor>) {
    if (!suspendableInitializer.isInitialized()) {
      return
    }

    val result = boardRepository.persist(getBoardsForSites(siteDescriptors))
    if (result is ModularResult.Error) {
      Logger.e(TAG, "boardRepository.persist() error", result.error)
      return
    }
  }

  private suspend fun persistBoards() {
    if (!suspendableInitializer.isInitialized()) {
      return
    }

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
          val innerBoardsMapCount = innerMap.values.count { chanBoard -> chanBoard.active }
          val innerOrdersMapCount = ordersMap[siteDescriptor]?.size ?: 0

          check(innerBoardsMapCount == innerOrdersMapCount) {
            "Inconsistency detected! innerBoardsMapCount (${innerBoardsMapCount}) != " +
              "innerOrdersMapCount (${innerOrdersMapCount})"
          }
        }
      }
    }
  }

  private fun getBoardsForSites(siteDescriptors: List<SiteDescriptor>): Map<SiteDescriptor, List<ChanBoard>> {
    ensureBoardsAndOrdersConsistency()
    val resultMap = mutableMapOf<SiteDescriptor, MutableList<ChanBoard>>()

    lock.read {
      siteDescriptors.forEach { siteDescriptor ->
        val boards = boardsMap[siteDescriptor]?.values
          ?: return@forEach

        resultMap[siteDescriptor] = mutableListWithCap(boards)
        resultMap[siteDescriptor]!!.addAll(boards)
      }
    }

    return resultMap
  }

  private fun getBoardsOrdered(): Map<SiteDescriptor, List<ChanBoard>> {
    ensureBoardsAndOrdersConsistency()
    val resultMap = mutableMapOf<SiteDescriptor, MutableList<ChanBoard>>()

    lock.read {
      ordersMap.forEach { (siteDescriptor, boardDescriptorsOrdered) ->
        if (!resultMap.containsKey(siteDescriptor)) {
          resultMap[siteDescriptor] = mutableListWithCap(boardDescriptorsOrdered)
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

  private fun boardsChanged() {
    if (isDevFlavor) {
      ensureBoardsAndOrdersConsistency()
    }

    boardsChangedSubject.onNext(Unit)
  }

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

  enum class BoardViewMode {
    AllBoards,
    OnlyActiveBoards,
    OnlyNonActiveBoards
  }

  companion object {
    private const val TAG = "BoardManager"
    private const val DEBOUNCE_TIME_MS = 500L
    private const val BOARD_MOVED_DEBOUNCE_TIME_MS = 100L
  }
}