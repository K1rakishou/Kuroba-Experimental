package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.ThreadDownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.DateTime
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadDownloadManager(
  private val appScope: CoroutineScope,
  private val threadDownloadRepository: ThreadDownloadRepository,
  private val chanPostRepository: ChanPostRepository
) {
  private val mutex = Mutex()
  private val suspendableInitializer = SuspendableInitializer<Unit>("ThreadDownloads")

  @GuardedBy("mutex")
  private val threadDownloadsMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ThreadDownload>(16)

  private val _threadDownloadUpdateFlow = MutableSharedFlow<Event>(extraBufferCapacity = 16)
  val threadDownloadUpdateFlow: SharedFlow<Event>
    get() = _threadDownloadUpdateFlow.asSharedFlow()

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (suspendableInitializer.isInitialized()) {
      return
    }

    Logger.d(TAG, "ThreadDownloadManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "ThreadDownloadManager initialization completed, took $duration")

  }

  fun isReady() = suspendableInitializer.isInitialized()

  fun initialize() {
    appScope.launch(Dispatchers.IO) {
      Logger.d(TAG, "ThreadDownloadingManager.initialize()")

      val initResult = threadDownloadRepository.initialize()
      if (initResult is ModularResult.Value) {
        val threadDownloads = initResult.value

        mutex.withLock {
          threadDownloads.forEach { threadDownload ->
            threadDownloadsMap[threadDownload.threadDescriptor] = threadDownload
          }
        }
      }

      suspendableInitializer.initWithModularResult(initResult.mapValue { Unit })
    }
  }

  suspend fun getStatus(threadDescriptor: ChanDescriptor.ThreadDescriptor): ThreadDownload.Status {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap[threadDescriptor]?.status
        ?: ThreadDownload.Status.Stopped
    }
  }

  suspend fun hasActiveThreads(): Boolean {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap.values.any { threadDownload -> threadDownload.status.isRunning() }
    }
  }

  suspend fun startDownloading(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    downloadMedia: Boolean = true
  ) {
    ensureInitialized()

    val alreadyExists = mutex.withLock {
      if (threadDownloadsMap.containsKey(threadDescriptor)) {
        return@withLock true
      }

      // Put a placeholder value in case multiple threads are trying to call this method to avoid
      // creating thread download twice or more.
      threadDownloadsMap[threadDescriptor] = ThreadDownload.DEFAULT_THREAD_DOWNLOAD
      return@withLock false
    }

    if (alreadyExists) {
      resumeThreadDownloadInternal(threadDescriptor)
    } else {
      startThreadDownloadInternal(threadDescriptor, downloadMedia)
    }
  }

  suspend fun stopDownloading(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    ensureInitialized()

    val updateData = mutex.withLock {
      val threadDownload = threadDownloadsMap[threadDescriptor]
      if (threadDownload == null) {
        return@withLock null
      }

      if (threadDownload.status != ThreadDownload.Status.Running) {
        return@withLock null
      }

      val prevStatus = threadDownload.status
      threadDownload.status = ThreadDownload.Status.Stopped

      return@withLock UpdateData(prevStatus, threadDownload)
    }

    if (updateData == null) {
      Logger.d(TAG, "stopDownloading() does not exist, threadDescriptor=$threadDescriptor")
      return
    }

    val updatedThreadDownload = updateData.threadDownload

    threadDownloadRepository.updateThreadDownload(updatedThreadDownload)
      .safeUnwrap { error ->
        Logger.e(TAG, "Failed to update thread download in the DB", error)
        mutex.withLock { threadDownloadsMap[threadDescriptor]?.status = updateData.prevStatus }
        return
      }

    mutex.withLock { threadDownloadsMap[updatedThreadDownload.threadDescriptor] = updatedThreadDownload }
    _threadDownloadUpdateFlow.emit(Event.StopDownload(threadDescriptor))

    Logger.d(TAG, "stopDownloading() success, threadDescriptor=$threadDescriptor")
  }

  suspend fun cancelDownloading(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    ensureInitialized()

    val threadDownload = mutex.withLock { threadDownloadsMap.remove(threadDescriptor) }
    if (threadDownload == null) {
      Logger.d(TAG, "cancelDownloading() does not exist, threadDescriptor=$threadDescriptor")
      return
    }

    threadDownloadRepository.deleteThreadDownload(threadDownload)
      .safeUnwrap { error ->
        Logger.e(TAG, "cancelDownloading() Failed to delete thread download from the DB", error)
        mutex.withLock { threadDownloadsMap[threadDownload.threadDescriptor] = threadDownload }
        return
      }

    _threadDownloadUpdateFlow.emit(Event.CancelDownload(threadDescriptor))
    Logger.d(TAG, "cancelDownloading() success, threadDescriptor=$threadDescriptor")
  }

  private fun ensureInitialized() {
    check(suspendableInitializer.isInitialized()) { "ThreadDownloadManager is not initialized yet! Use " }
  }

  private suspend fun startThreadDownloadInternal(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    downloadMedia: Boolean
  ) {
    var success = false

    val databaseId = chanPostRepository.createEmptyThreadIfNotExists(threadDescriptor)
      .peekError { error -> Logger.e(TAG, "Failed to create empty thread in the DB", error) }
      .valueOrNull() ?: -1L

    if (databaseId >= 0L) {
      val threadDownload = ThreadDownload(
        ownerThreadDatabaseId = databaseId,
        threadDescriptor = threadDescriptor,
        downloadMedia = downloadMedia,
        status = ThreadDownload.Status.Running,
        createdOn = DateTime.now(),
        lastUpdateTime = null
      )

      val threadDownloadCreated = threadDownloadRepository.createThreadDownload(threadDownload)
        .peekError { error -> Logger.e(TAG, "Failed to create thread download in the DB", error) }
        .isValue()

      if (threadDownloadCreated) {
        mutex.withLock { threadDownloadsMap[threadDownload.threadDescriptor] = threadDownload }
        success = true
      } else {
        chanPostRepository.deleteThread(threadDescriptor)
      }
    }

    if (!success) {
      mutex.withLock { threadDownloadsMap.remove(threadDescriptor) }
    } else {
      _threadDownloadUpdateFlow.emit(Event.StartDownload(threadDescriptor))
    }

    Logger.d(TAG, "startThreadDownloadInternal() threadDescriptor=$threadDescriptor, " +
      "downloadMedia=$downloadMedia, success=$success")
  }

  private suspend fun resumeThreadDownloadInternal(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val updateData = mutex.withLock {
      val threadDownload = threadDownloadsMap[threadDescriptor]
      if (threadDownload == null) {
        return@withLock null
      }

      if (threadDownload.status != ThreadDownload.Status.Stopped) {
        return@withLock null
      }

      val prevStatus = threadDownload.status
      threadDownload.status = ThreadDownload.Status.Running

      return@withLock UpdateData(prevStatus, threadDownload)
    }

    if (updateData == null) {
      Logger.d(TAG, "startDownloading() already running/completed or does not exist, " +
        "threadDescriptor=$threadDescriptor")
      return
    }

    val updatedThreadDownload = updateData.threadDownload

    threadDownloadRepository.updateThreadDownload(updatedThreadDownload)
      .safeUnwrap { error ->
        Logger.e(TAG, "Failed to update thread download in the DB", error)
        mutex.withLock { threadDownloadsMap[threadDescriptor]?.status = updateData.prevStatus }
        return
      }

    mutex.withLock { threadDownloadsMap[updatedThreadDownload.threadDescriptor] = updatedThreadDownload }
    _threadDownloadUpdateFlow.emit(Event.StartDownload(threadDescriptor))

    Logger.d(TAG, "resumeThreadDownloadInternal() success, threadDescriptor=$threadDescriptor")
  }

  sealed class Event {
    data class StartDownload(val threadDescriptor: ChanDescriptor.ThreadDescriptor) : Event()
    data class StopDownload(val threadDescriptor: ChanDescriptor.ThreadDescriptor) : Event()
    data class CancelDownload(val threadDescriptor: ChanDescriptor.ThreadDescriptor) : Event()
  }

  class UpdateData(
    val prevStatus: ThreadDownload.Status,
    val threadDownload: ThreadDownload
  )

  companion object {
    private const val TAG = "ThreadDownloadManager"
  }

}