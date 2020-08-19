package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.SuspendableInitializer
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
  private val boardRepository: BoardRepository
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("BoardManager")

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val boards = mutableMapWithCap<BoardDescriptor, ChanBoard>(512)

  fun loadBoards() {
    appScope.launch {
      val result = boardRepository.loadBoards()
      if (result is ModularResult.Error) {
        suspendableInitializer.initWithError(result.error)
        return@launch
      }

      result as ModularResult.Value

      lock.write {
        result.value.forEach { chanBoard ->
          boards[chanBoard.boardDescriptor] = chanBoard
        }
      }

      suspendableInitializer.initWithValue(Unit)
    }
  }

  fun getBoard(boardDescriptor: BoardDescriptor): ChanBoard? {
    check(isReady()) { "BoardManager is not ready yet! Use awaitUntilInitialized()" }

    return lock.read { boards[boardDescriptor] }
  }

  fun activeBoardsCount(siteDescriptor: SiteDescriptor): Int {
    // TODO(KurobaEx):
    return 0
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

  private fun isReady() = suspendableInitializer.isInitialized()

  companion object {
    private const val TAG = "BoardManager"
  }
}