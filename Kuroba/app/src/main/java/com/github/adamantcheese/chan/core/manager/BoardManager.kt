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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class BoardManager(
  private val appScope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val boardRepository: BoardRepository
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("BoardManager")
  private val persistBoardsDebouncer = SuspendDebouncer(appScope)

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val boardsMap = mutableMapOf<SiteDescriptor, MutableMap<BoardDescriptor, ChanBoard>>()
  @GuardedBy("lock")
  private val ordersMap = mutableMapOf<SiteDescriptor, MutableList<BoardDescriptor>>()

  fun loadBoards() {
    appScope.launch {
      val result = boardRepository.initialize()
      if (result is ModularResult.Error) {
        Logger.e(TAG, "boardRepository.initialize() error", result.error)
        suspendableInitializer.initWithError(result.error)
        return@launch
      }

      try {
        result as ModularResult.Value

        lock.write {
          result.value.forEach { (siteDescriptor, chanBoards) ->
            val sortedBoards = chanBoards.sortedByDescending { chanBoard -> chanBoard.order }
            ordersMap[siteDescriptor] = mutableListWithCap(64)
            boardsMap[siteDescriptor] = mutableMapWithCap(64)

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
  }

  fun createBoard(board: ChanBoard) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    // TODO(KurobaEx):
  }

  fun viewAllBoards(func: (ChanBoard) -> Unit) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    lock.read {
      boardsMap.values.forEach { innerMap ->
        innerMap.values.forEach { chanBoard ->
          func(chanBoard)
        }
      }
    }
  }

  fun updateAvailableBoardsForSite(boards: List<ChanBoard>) {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }
    ensureBoardsAndOrdersConsistency()

    lock.write {
      boards.forEach { chanBoard ->
        val siteDescriptor = chanBoard.boardDescriptor.siteDescriptor

        val alreadyContains = boardsMap[siteDescriptor]?.contains(chanBoard.boardDescriptor)
          ?: false

        boardsMap[siteDescriptor]?.put(chanBoard.boardDescriptor, chanBoard)

        if (!alreadyContains) {
          ordersMap[siteDescriptor]?.add(chanBoard.boardDescriptor)
        } else {
          val index = ordersMap[siteDescriptor]?.indexOf(chanBoard.boardDescriptor) ?: -1
          if (index >= 0) {
            ordersMap[siteDescriptor]?.set(index, chanBoard.boardDescriptor)
          }
        }
      }
    }

    persistBoardsDebouncer.post(500) { boardRepository.updateBoards(boards) }
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
      return@read boardsMap[siteDescriptor]?.keys?.count { boardDescriptor ->
        boardDescriptor.siteDescriptor == siteDescriptor
      } ?: 0
    }
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

  private fun isReady() = suspendableInitializer.isInitialized()

  companion object {
    private const val TAG = "BoardManager"
  }
}