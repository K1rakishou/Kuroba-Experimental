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
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.common.putIfNotContainsLazy
import com.github.k1rakishou.common.withLockNonCancellable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.BooleanSetting
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
    return mutex.withLock {
      when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          return@withLock false
        }
        is ChanDescriptor.CatalogDescriptor -> {
          val boardDescriptor = chanDescriptor.boardDescriptor()
          lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          return@withLock lastThreadMap[boardDescriptor]!!.prepareToPost()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          val boardDescriptor = chanDescriptor.boardDescriptor
          lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          return@withLock lastReplyMap[boardDescriptor]!!.prepareToPost()
        }
      }
    }
  }

  suspend fun endPostingAttempt(chanDescriptor: ChanDescriptor) {
    mutex.withLock {
      when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          // no-op
        }
        is ChanDescriptor.CatalogDescriptor -> {
          val boardDescriptor = chanDescriptor.boardDescriptor()

          lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          lastThreadMap[boardDescriptor]!!.endPosting()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          val boardDescriptor = chanDescriptor.boardDescriptor()

          lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
          lastReplyMap[boardDescriptor]!!.endPosting()
        }
      }
    }
  }

  suspend fun onPostAttemptFinished(
    chanDescriptor: ChanDescriptor,
    postedSuccessfully: Boolean = false,
    newCooldownInfo: CooldownInfo? = null
  ) {
    Logger.d(TAG, "onPostAttemptFinished($chanDescriptor, newCooldownInfo=$newCooldownInfo)")

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return
    }

    val boardDescriptor = chanDescriptor.boardDescriptor()

    mutex.withLockNonCancellable {
      if (postedSuccessfully) {
        when (chanDescriptor) {
          is ChanDescriptor.CompositeCatalogDescriptor -> {
            return@withLockNonCancellable
          }
          is ChanDescriptor.CatalogDescriptor -> {
            lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
            lastThreadMap[boardDescriptor]!!.updateLastReplyAttemptTime()
          }
          is ChanDescriptor.ThreadDescriptor -> {
            lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
            lastReplyMap[boardDescriptor]!!.updateLastReplyAttemptTime()
          }
        }
      }

      cooldownInfoMap.putIfNotContainsLazy(boardDescriptor) { CooldownInfo(boardDescriptor) }

      if (newCooldownInfo != null) {
        cooldownInfoMap[boardDescriptor] = newCooldownInfo.mergeWithPrev(cooldownInfoMap[boardDescriptor])
      } else {
        // Reset the cooldowns after posting
        cooldownInfoMap[boardDescriptor] = CooldownInfo(boardDescriptor)
      }

      Logger.d(TAG, "onPostAttemptFinished($chanDescriptor, cooldownInfo=${cooldownInfoMap[boardDescriptor]})")
    }
  }

  suspend fun getTimeUntilNextThreadCreationOrReply(
    chanDescriptor: ChanDescriptor,
    replyMode: ReplyMode,
    hasAttachedImages: Boolean = false
  ): Long {
    Logger.d(TAG, "getTimeUntilNextThreadCreationOrReply($chanDescriptor, $replyMode)")

    val ignoreReplyCooldowns = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.requireSettingBySettingId<BooleanSetting>(SiteSetting.SiteSettingId.IgnoreReplyCooldowns)
      ?.get()

    if (ignoreReplyCooldowns == true) {
      Logger.d(TAG, "getTimeUntilNextThreadCreationOrReply($chanDescriptor, $replyMode) " +
        "ignoreReplyCooldowns setting is enabled, skipping cooldown checks")
      return 0
    }

    return when (chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor -> 0
      is ChanDescriptor.CatalogDescriptor -> {
        getTimeUntilNewThread(chanDescriptor.boardDescriptor, replyMode)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        getTimeUntilReply(chanDescriptor.boardDescriptor, replyMode, hasAttachedImages)
      }
    }
  }

  private suspend fun getTimeUntilReply(
    boardDescriptor: BoardDescriptor,
    replyMode: ReplyMode,
    hasAttachedImages: Boolean
  ): Long {
    val lastReply = mutex.withLock {
      lastReplyMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
      lastReplyMap[boardDescriptor]!!
    }

    if (lastReply.lastReplyTimeMs <= 0) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor, $replyMode) lastReplyTimeMs <= 0")
      return 0L
    }

    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor, $replyMode) site (${boardDescriptor.siteDescriptor}) == null")
      return 0L
    }

    val postingWithPasscode = replyMode == ReplyMode.ReplyModeUsePasscode
      && site.actions().isLoggedIn()

    val lastReplyCooldown = checkLastReplyCooldown(
      creatingNewThread = false,
      boardDescriptor = boardDescriptor,
      hasAttachedImages = hasAttachedImages,
      lastReply = lastReply,
      postingWithPasscode = postingWithPasscode
    )

    if (lastReplyCooldown != null && lastReplyCooldown > 0) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor, $replyMode) lastReplyCooldown > 0 " +
        "(lastReplyCooldown=$lastReplyCooldown)")
      return lastReplyCooldown
    }

    val cooldowns = mutex.withLock {
      cooldownInfoMap.putIfNotContainsLazy(boardDescriptor) { CooldownInfo(boardDescriptor) }
      cooldownInfoMap[boardDescriptor]!!
    }

    var currentPostingCooldownMs = cooldowns.currentPostingCooldownMs
    if (currentPostingCooldownMs <= 0L) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor, $replyMode) currentPostingCooldownMs <= 0")
      return 0L
    }

    if (postingWithPasscode) {
      Logger.d(TAG, "getTimeUntilReply($boardDescriptor, $replyMode) halving " +
        "currentPostingCooldownMs because of passcode")

      currentPostingCooldownMs /= 2
    }

    val now = System.currentTimeMillis()
    val actualWaitTime = currentPostingCooldownMs - (now - lastReply.lastReplyTimeMs)

    Logger.d(TAG, "getTimeUntilReply($boardDescriptor, $replyMode) actualWaitTime=$actualWaitTime, " +
      "currentPostingCooldownMs=${currentPostingCooldownMs}, now=${now}, lastReplyTimeMs=${lastReply.lastReplyTimeMs}")

    if (actualWaitTime < 0) {
      return 0L
    }

    return actualWaitTime
  }

  private suspend fun getTimeUntilNewThread(boardDescriptor: BoardDescriptor, replyMode: ReplyMode): Long {
    val lastReply = mutex.withLock {
      lastThreadMap.putIfNotContainsLazy(boardDescriptor) { LastReply(boardDescriptor) }
      lastThreadMap[boardDescriptor]!!
    }

    if (lastReply.lastReplyTimeMs <= 0) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor, $replyMode) lastReplyTimeMs <= 0")
      return 0L
    }

    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor, $replyMode) " +
        "site (${boardDescriptor.siteDescriptor}) == null")
      return 0L
    }

    val postingWithPasscode = replyMode == ReplyMode.ReplyModeUsePasscode && site.actions().isLoggedIn()

    val lastReplyCooldown = checkLastReplyCooldown(
      creatingNewThread = true,
      boardDescriptor = boardDescriptor,
      hasAttachedImages = false,
      lastReply = lastReply,
      postingWithPasscode = postingWithPasscode
    )

    if (lastReplyCooldown != null && lastReplyCooldown > 0) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor, $replyMode) " +
        "lastReplyCooldown > 0 (lastReplyCooldown=$lastReplyCooldown)")
      return lastReplyCooldown
    }

    val cooldowns = mutex.withLock {
      cooldownInfoMap.putIfNotContainsLazy(boardDescriptor) { CooldownInfo(boardDescriptor) }
      cooldownInfoMap[boardDescriptor]!!
    }

    var currentPostingCooldownMs = cooldowns.currentPostingCooldownMs
    if (currentPostingCooldownMs <= 0L) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor, $replyMode) currentPostingCooldownMs <= 0")
      return 0L
    }

    if (replyMode == ReplyMode.ReplyModeUsePasscode && site.actions().isLoggedIn()) {
      Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor, $replyMode) halving " +
        "currentPostingCooldownMs because of passcode")

      currentPostingCooldownMs /= 2
    }

    val now = System.currentTimeMillis()
    val actualWaitTime = currentPostingCooldownMs - (now - lastReply.lastReplyTimeMs)

    Logger.d(TAG, "getTimeUntilNewThread($boardDescriptor, $replyMode) actualWaitTime=$actualWaitTime, " +
      "currentPostingCooldownMs=${currentPostingCooldownMs}, now=${now}, lastReplyTimeMs=${lastReply.lastReplyTimeMs}")

    if (actualWaitTime < 0) {
      return 0L
    }

    return actualWaitTime
  }

  private fun checkLastReplyCooldown(
    creatingNewThread: Boolean,
    boardDescriptor: BoardDescriptor,
    hasAttachedImages: Boolean,
    lastReply: LastReply,
    postingWithPasscode: Boolean
  ): Long? {
    val chanBoard = boardManager.byBoardDescriptor(boardDescriptor)
    if (chanBoard == null) {
      return null
    }

    val replyCooldownSeconds = when {
      creatingNewThread -> chanBoard.cooldownThreads
      hasAttachedImages -> chanBoard.cooldownImages
      else -> chanBoard.cooldownReplies
    }

    if (replyCooldownSeconds <= 0) {
      return null
    }

    val willBeAbleToPostAt = lastReply.lastReplyTimeMs + (replyCooldownSeconds * 1000L) + ADDITIONAL_TIME_MS
    val currentTime = System.currentTimeMillis()

    if (willBeAbleToPostAt > currentTime) {
      // We can't post yet because lastReplyTime + boardCooldown is greater than current time
      val cooldownMs = willBeAbleToPostAt - currentTime

      val resultCooldownMs = if (postingWithPasscode) {
        cooldownMs / 2
      } else {
        cooldownMs
      }

      Logger.d(TAG, "checkLastReplyCooldown($boardDescriptor, $hasAttachedImages) " +
        "currentTime ($currentTime) > willBeAbleToPostAt ($willBeAbleToPostAt), " +
        "postingWithPasscode=${postingWithPasscode}, cooldownMs=${cooldownMs}, resultCooldownMs=${resultCooldownMs}")

      return resultCooldownMs
    }

    return null
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

    private const val ADDITIONAL_TIME_MS = 5000L
  }

}