package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

class ChanThreadTicker(
  private val scope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val archivesManager: ArchivesManager,
  private val action: suspend (ChanDescriptor) -> Unit
) {
  private val debouncer = DebouncingCoroutineExecutor(scope)
  private val chanTickerData = ChanTickerData()

  val currentChanDescriptor: ChanDescriptor?
    get() = chanTickerData.currentChanDescriptor()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val actor = scope.actor<TickerAction>(capacity = Channel.UNLIMITED) {
    consumeEach { tickerAction ->
      when (tickerAction) {
        is TickerAction.StartOrResetTicker -> {
          chanTickerData.stopJob()
          Logger.d(TAG, "StartOrResetTicker stopping previous job")

          val tickerJob = launch {
            try {
              val waitTimeMillis = chanTickerData.waitTimeMillis()
              Logger.d(TAG, "StartOrResetTicker scheduled, waiting ${waitTimeMillis}ms")
              delay(waitTimeMillis)

              run {
                Logger.d(TAG, "StartOrResetTicker run action begin")

                try {
                  action.invoke(tickerAction.chanDescriptor)
                } catch (error: Throwable) {
                  if (error is CancellationException || isDevFlavor) {
                    throw error
                  }

                  Logger.e(TAG, "Error while trying to execute action in tickerWorkLoop", error)
                }

                Logger.d(TAG, "StartOrResetTicker run action end")
              }

              val nextTimeoutIndex = increaseCurrentTimeoutIndex()
              val nextWaitTimeSeconds = getWaitTimeSeconds()

              if (nextWaitTimeSeconds == null || nextTimeoutIndex == null) {
                return@launch
              }

              chanTickerData.updateCurrentTimeoutIndex(nextTimeoutIndex)
              chanTickerData.updateWaitTimeSeconds(nextWaitTimeSeconds)

              Logger.d(TAG, "StartOrResetTicker done, " +
                "nextTimeoutIndex=${nextTimeoutIndex}, " +
                "nextWaitTimeSeconds=${nextWaitTimeSeconds}")

            } catch (error: Throwable) {
              if (error is CancellationException) {
                return@launch
              }

              if (isDevFlavor) {
                throw error
              }

              Logger.e(TAG, "StartOrResetTicker error", error)
            }
          }

          chanTickerData.setJob(tickerJob)
        }
        TickerAction.StopTicker -> {
          Logger.d(TAG, "StopTicker")
          chanTickerData.stopJob()
        }
      }
    }

  }

  fun startTicker(descriptor: ChanDescriptor) {
    Logger.d(TAG, "startTicker($descriptor)")

    chanTickerData.resetAll()
    chanTickerData.updateCurrentChanDescriptor(descriptor)

    kickTicker(resetTimer = false)
  }

  fun resetEverythingAndKickTicker() {
    Logger.d(TAG, "resetEverythingAndKickTicker()")

    chanTickerData.resetAll()
    kickTicker(resetTimer = false)
  }

  fun kickTicker(resetTimer: Boolean) {
    Logger.d(TAG, "kickTicker($resetTimer)")

    if (resetTimer) {
      val chanDescriptor = chanTickerData.currentChanDescriptor()
        ?: return

      val nextWaitTimeSeconds = getWaitTimeSecondsByTimeoutIndex(chanDescriptor, 0)
      chanTickerData.kickTimer(nextWaitTimeSeconds)
    }

    val descriptor = chanTickerData.currentChanDescriptor()
    if (descriptor == null) {
      Logger.d(TAG, "kickTicker() called with null current descriptor")
      return
    }

    // We don't use ticking for catalog (meaning we don't auto update catalogs) so just invoke
    // the action with a slight delay.
    if (descriptor is ChanDescriptor.CatalogDescriptor) {
      actor.offer(TickerAction.StopTicker)
      debouncer.post(DEBOUNCE_TIMEOUT) { action.invoke(descriptor) }

      Logger.d(TAG, "kickTicker() called with catalog descriptor, ticking right away")
      return
    }

    val tickerAction = TickerAction.StartOrResetTicker(
      chanDescriptor = descriptor as ChanDescriptor.ThreadDescriptor
    )

    actor.offer(tickerAction)
  }

  fun resetTicker() {
    Logger.d(TAG, "resetTicker()")

    val chanDescriptor = chanTickerData.currentChanDescriptor()
    if (chanDescriptor == null) {
      Logger.d(TAG, "resetTicker() called with null current descriptor")
      return
    }

    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      actor.offer(TickerAction.StopTicker)
      debouncer.post(DEBOUNCE_TIMEOUT) { action.invoke(chanDescriptor) }

      Logger.d(TAG, "resetTicker() called with catalog descriptor, ticking right away")
      return
    }

    val tickerAction = TickerAction.StartOrResetTicker(
      chanDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor
    )

    actor.offer(tickerAction)
  }

  fun stopTicker(resetCurrentChanDescriptor: Boolean) {
    Logger.d(TAG, "stopTicker()")

    chanTickerData.resetAll()

    if (resetCurrentChanDescriptor) {
      chanTickerData.resetCurrentChanDescriptor()
    }

    actor.offer(TickerAction.StopTicker)
  }

  fun timeUntilLoadMoreMs(): Long? = chanTickerData.getTimeUntilLoadMoreMs()

  @Synchronized
  private fun increaseCurrentTimeoutIndex(): Int? {
    val currentDescriptor = chanTickerData.currentChanDescriptor()
      ?: return null
    val currentTimeoutIndex = chanTickerData.getCurrentTimeoutIndex()

    val isArchiveDescriptor = archivesManager.isSiteArchive(currentDescriptor.siteDescriptor())
    val maxIndex = if (isArchiveDescriptor) {
      ARCHIVE_WATCH_TIMEOUTS.lastIndex
    } else {
      NORMAL_WATCH_TIMEOUTS.lastIndex
    }

    return min(currentTimeoutIndex + 1, maxIndex)
  }

  @Synchronized
  private fun getWaitTimeSeconds(): Long? {
    val currentDescriptor = chanTickerData.currentChanDescriptor()
      ?: return null
    val currentTimeoutIndex = chanTickerData.getCurrentTimeoutIndex()

    return getWaitTimeSecondsByTimeoutIndex(currentDescriptor, currentTimeoutIndex)
  }

  @Synchronized
  private fun getWaitTimeSecondsByTimeoutIndex(
    currentDescriptor: ChanDescriptor,
    currentTimeoutIndex: Int
  ): Long {
    val isArchiveDescriptor = archivesManager.isSiteArchive(currentDescriptor.siteDescriptor())
    if (isArchiveDescriptor) {
      return ARCHIVE_WATCH_TIMEOUTS.getOrElse(currentTimeoutIndex) {
        return@getOrElse ARCHIVE_WATCH_TIMEOUTS[currentTimeoutIndex]
      }
    } else {
      return NORMAL_WATCH_TIMEOUTS.getOrElse(currentTimeoutIndex) {
        return@getOrElse NORMAL_WATCH_TIMEOUTS[currentTimeoutIndex]
      }
    }
  }

  private class ChanTickerData(
    private var currentChanDescriptor: ChanDescriptor? = null,
    private var currentTimeoutIndex: Int = 0,
    private var tickerJob: Job? = null
  ) {
    private var waitTimeSeconds: Long = 0
    private var lastLoadTime: Long = 0

    @Synchronized
    fun isTicking(): Boolean = tickerJob != null

    @Synchronized
    fun waitTimeMillis(): Long = waitTimeSeconds * 1000L

    @Synchronized
    fun getTimeUntilLoadMoreMs(): Long {
      if (lastLoadTime == 0L) {
        return 0
      }

      return lastLoadTime + (waitTimeSeconds * 1000L) - System.currentTimeMillis()
    }

    @Synchronized
    fun setJob(newJob: Job) {
      tickerJob = newJob
    }

    @Synchronized
    fun stopJob() {
      tickerJob?.cancel()
      tickerJob = null
    }

    @Synchronized
    fun resetAll() {
      this.currentTimeoutIndex = 0
      this.waitTimeSeconds = 0
      this.lastLoadTime = 0
    }

    @Synchronized
    fun kickTimer(nextWaitTimeSeconds: Long) {
      this.currentTimeoutIndex = 0
      this.waitTimeSeconds = nextWaitTimeSeconds
    }

    @Synchronized
    fun updateCurrentTimeoutIndex(newTimeoutIndex: Int) {
      this.currentTimeoutIndex = newTimeoutIndex
    }

    @Synchronized
    fun updateWaitTimeSeconds(waitTimeSeconds: Long) {
      this.lastLoadTime = System.currentTimeMillis()
      this.waitTimeSeconds = waitTimeSeconds
    }

    @Synchronized
    fun getCurrentTimeoutIndex(): Int = this.currentTimeoutIndex

    @Synchronized
    fun currentChanDescriptor(): ChanDescriptor? = currentChanDescriptor

    @Synchronized
    fun updateCurrentChanDescriptor(newChanDescriptor: ChanDescriptor) {
      this.currentChanDescriptor = newChanDescriptor
    }

    @Synchronized
    fun resetCurrentChanDescriptor() {
      this.currentChanDescriptor = null
    }

  }

  private sealed class TickerAction {
    class StartOrResetTicker(val chanDescriptor: ChanDescriptor.ThreadDescriptor): TickerAction()
    object StopTicker : TickerAction()
  }

  companion object {
    private const val TAG = "ChanTicker"
    private const val DEBOUNCE_TIMEOUT = 1000L

    private val NORMAL_WATCH_TIMEOUTS = longArrayOf(15, 20, 30, 45, 60, 90, 120, 180, 240, 300, 450, 600, 750, 1000)
    private val ARCHIVE_WATCH_TIMEOUTS = longArrayOf(300, 600, 1200, 1800, 2400, 3600)
  }
}