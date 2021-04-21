/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.posting

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.common.putIfNotContainsLazy
import com.github.k1rakishou.common.withLockNonCancellable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LastReplyRepository(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val lastReplyMap = HashMap<BoardDescriptor, LastReply>()
  @GuardedBy("mutex")
  private val lastThreadMap = HashMap<BoardDescriptor, LastReply>()
  @GuardedBy("mutex")
  private val cooldownInfoMap = HashMap<BoardDescriptor, CooldownInfo>()

  suspend fun attemptToStartPosting(chanDescriptor: ChanDescriptor): Boolean {
    val boardDescriptor = chanDescriptor.boardDescriptor()

    return mutex.withLock {
      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          return@withLock lastThreadMap[boardDescriptor]!!.prepareToPost()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          return@withLock lastReplyMap[boardDescriptor]!!.prepareToPost()
        }
      }
    }
  }

  suspend fun endPostingAttempt(chanDescriptor: ChanDescriptor) {
    val boardDescriptor = chanDescriptor.boardDescriptor()

    mutex.withLock {
      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          lastThreadMap[boardDescriptor]!!.endPosting()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          lastReplyMap[boardDescriptor]!!.endPosting()
        }
      }
    }
  }

  suspend fun onPostAttemptFinished(
    chanDescriptor: ChanDescriptor,
    newCooldownInfo: CooldownInfo? = null
  ) {
    Logger.d(TAG, "onPostAttemptFinished($chanDescriptor, $newCooldownInfo)")

    mutex.withLockNonCancellable {
      val boardDescriptor = chanDescriptor.boardDescriptor()

      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          lastThreadMap[boardDescriptor]!!.updateLastReplyAttemptTime()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          lastReplyMap[boardDescriptor]!!.updateLastReplyAttemptTime()
        }
      }

      cooldownInfoMap.putIfNotContainsLazy(boardDescriptor) { CooldownInfo(boardDescriptor) }

      if (newCooldownInfo != null) {
        cooldownInfoMap[boardDescriptor] = newCooldownInfo.mergeWithPrev(cooldownInfoMap[boardDescriptor])
      } else {
        // Reset the cooldowns after posting
        cooldownInfoMap[boardDescriptor] = CooldownInfo(boardDescriptor)
      }
    }
  }

  suspend fun getTimeUntilNextThreadCreationOrReply(chanDescriptor: ChanDescriptor): Long {
    return when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> getTimeUntilNewThread(chanDescriptor.boardDescriptor)
      is ChanDescriptor.ThreadDescriptor -> getTimeUntilReply(chanDescriptor.boardDescriptor)
    }
  }

  suspend fun getTimeUntilReply(boardDescriptor: BoardDescriptor): Long {
    val lastReply = mutex.withLock {
      lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
      lastReplyMap[boardDescriptor]!!
    }

    if (lastReply.lastReplyTimeMs <= 0) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor) lastReplyTimeMs <= 0")
      return 0L
    }

    val cooldowns = mutex.withLock {
      cooldownInfoMap.putIfNotContainsLazy(boardDescriptor) { CooldownInfo(boardDescriptor) }
      cooldownInfoMap[boardDescriptor]!!
    }

    var currentPostingCooldownMs = cooldowns.currentPostingCooldownMs
    if (currentPostingCooldownMs <= 0L) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor) currentPostingCooldownMs <= 0")
      return 0L
    }

    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor) site (${boardDescriptor.siteDescriptor}) == null")
      return currentPostingCooldownMs
    }

    if (site.actions().isLoggedIn()) {
      currentPostingCooldownMs /= 2
    }

    val now = System.currentTimeMillis()
    val actualWaitTime = currentPostingCooldownMs - (now - lastReply.lastReplyTimeMs)

    if (actualWaitTime < 0) {
      Logger.d(
        TAG, "getTimeUntilReply($boardDescriptor) actualWaitTime < 0 ($actualWaitTime), " +
        "currentPostingCooldownMs=${currentPostingCooldownMs}, now=${now}, " +
        "lastReplyTimeMs=${lastReply.lastReplyTimeMs}")

      return 0L
    }

    return actualWaitTime
  }

  suspend fun getTimeUntilNewThread(boardDescriptor: BoardDescriptor): Long {
    val lastReply = mutex.withLock {
      lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
      lastThreadMap[boardDescriptor]!!
    }

    if (lastReply.lastReplyTimeMs <= 0) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor) lastReplyTimeMs <= 0")
      return 0L
    }

    val cooldowns = mutex.withLock {
      cooldownInfoMap.putIfNotContainsLazy(boardDescriptor) { CooldownInfo(boardDescriptor) }
      cooldownInfoMap[boardDescriptor]!!
    }

    var currentPostingCooldownMs = cooldowns.currentPostingCooldownMs
    if (currentPostingCooldownMs <= 0L) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor) currentPostingCooldownMs <= 0")
      return 0L
    }

    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor) site (${boardDescriptor.siteDescriptor}) == null")
      return currentPostingCooldownMs
    }

    if (site.actions().isLoggedIn()) {
      currentPostingCooldownMs /= 2
    }

    val now = System.currentTimeMillis()
    val actualWaitTime = currentPostingCooldownMs - (now - lastReply.lastReplyTimeMs)

    if (actualWaitTime < 0) {
      Logger.d(
        TAG, "getTimeUntilNewThread($boardDescriptor) actualWaitTime < 0 ($actualWaitTime), " +
        "currentPostingCooldownMs=${currentPostingCooldownMs}, now=${now}, " +
        "lastReplyTimeMs=${lastReply.lastReplyTimeMs}")

      return 0L
    }

    return actualWaitTime
  }

  class LastReply(
    private val boardDescriptor: BoardDescriptor
  ) {
    private var _lastReplyTimeMs: Long = 0L
    private val _canPost = AtomicBoolean(true)

    val canPost: Boolean
      get() = _canPost.get()

    val lastReplyTimeMs: Long
      get() = _lastReplyTimeMs

    fun updateLastReplyAttemptTime() {
      _lastReplyTimeMs = System.currentTimeMillis()
      Logger.d(TAG, "updateLastReplyAttemptTime($boardDescriptor), lastReplyTimeMs=${_lastReplyTimeMs}")
    }

    fun prepareToPost(): Boolean {
      val result = _canPost.compareAndSet(true, false)
      Logger.d(TAG, "prepareToPost($boardDescriptor) -> $result")

      return result
    }

    fun endPosting() {
      Logger.d(TAG, "endPosting($boardDescriptor)")
      _canPost.set(true)
    }

    override fun toString(): String {
      return "LastReply(boardDescriptor=$boardDescriptor, lastReplyTimeMs=$lastReplyTimeMs)"
    }
  }

  class CooldownInfo(
    val boardDescriptor: BoardDescriptor,
    currentPostingCooldownMs: Long = 0L
  ) {
    var currentPostingCooldownMs: Long = currentPostingCooldownMs
      set(value) { field = value.coerceIn(0L, MAX_TIMEOUT_MS) }

    fun copy(): CooldownInfo {
      return CooldownInfo(
        boardDescriptor = boardDescriptor,
        currentPostingCooldownMs = currentPostingCooldownMs
      )
    }

    override fun toString(): String {
      return "CooldownInfo(boardDescriptor=$boardDescriptor, currentPostingCooldownMs=$currentPostingCooldownMs)"
    }

    fun mergeWithPrev(prevCooldownInfo: CooldownInfo?): CooldownInfo {
      if (prevCooldownInfo == null) {
        return this
      }

      return CooldownInfo(
        boardDescriptor,
        Math.max(prevCooldownInfo.currentPostingCooldownMs, currentPostingCooldownMs)
      )
    }

    companion object {
      val MAX_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5)
    }

  }

  companion object {
    private const val TAG = "LastReplyRepository"
  }

}