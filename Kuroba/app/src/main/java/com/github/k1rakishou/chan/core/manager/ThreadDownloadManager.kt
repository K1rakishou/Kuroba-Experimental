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

  suspend fun getAllActiveThreads(): List<ChanDescriptor.ThreadDescriptor> {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap
        .entries
        .filter { (_, threadDownload) -> threadDownload.status.isRunning() }
        .map { (_, threadDownload) -> threadDownload.threadDescriptor }
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

    val updated = updateThreadDownload(threadDescriptor, updaterFunc = { threadDownload ->
      if (threadDownload.status != ThreadDownload.Status.Running) {
        return@updateThreadDownload null
      }

      return@updateThreadDownload threadDownload.copy(status = ThreadDownload.Status.Stopped)
    })

    if (updated) {
      _threadDownloadUpdateFlow.emit(Event.StopDownload(threadDescriptor))
    }

    Logger.d(TAG, "stopDownloading() success=$updated, threadDescriptor=$threadDescriptor")
  }

  suspend fun completeDownloading(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    ensureInitialized()

    val updated = updateThreadDownload(threadDescriptor, updaterFunc = { threadDownload ->
      if (threadDownload.status != ThreadDownload.Status.Running
        && threadDownload.status != ThreadDownload.Status.Stopped) {
        return@updateThreadDownload null
      }

      return@updateThreadDownload threadDownload.copy(status = ThreadDownload.Status.Completed)
    })

    if (updated) {
      _threadDownloadUpdateFlow.emit(Event.CompleteDownload(threadDescriptor))
    }

    Logger.d(TAG, "completeDownloading() success=$updated, threadDescriptor=$threadDescriptor")
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

  private suspend fun updateThreadDownload(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    updaterFunc: (ThreadDownload) -> ThreadDownload?
  ): Boolean {
    ensureInitialized()

    val updateData = mutex.withLock {
      val oldThreadDownload = threadDownloadsMap[threadDescriptor]
      if (oldThreadDownload == null) {
        return@withLock null
      }

      val newThreadDownload = updaterFunc(oldThreadDownload)
      if (newThreadDownload == null) {
        return@withLock null
      }

      check(oldThreadDownload !== newThreadDownload) {
        "updaterFunc must return an updated copy of ThreadDownload"
      }

      return@withLock UpdateData(oldThreadDownload, newThreadDownload)
    }

    if (updateData == null) {
      Logger.d(TAG, "stopDownloading() does not exist, threadDescriptor=$threadDescriptor")
      return false
    }

    val newThreadDownload = updateData.newThreadDownload

    threadDownloadRepository.updateThreadDownload(newThreadDownload)
      .safeUnwrap { error ->
        Logger.e(TAG, "Failed to update thread download in the DB", error)
        mutex.withLock { threadDownloadsMap[threadDescriptor] = updateData.oldThreadDownload }
        return false
      }

    mutex.withLock { threadDownloadsMap[newThreadDownload.threadDescriptor] = newThreadDownload }
    return true
  }

  private suspend fun resumeThreadDownloadInternal(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val updated = updateThreadDownload(threadDescriptor, updaterFunc = { threadDownload ->
      if (threadDownload.status != ThreadDownload.Status.Stopped) {
        return@updateThreadDownload null
      }

      return@updateThreadDownload threadDownload.copy(status = ThreadDownload.Status.Running)
    })

    if (updated) {
      _threadDownloadUpdateFlow.emit(Event.StartDownload(threadDescriptor))
    }

    Logger.d(TAG, "resumeThreadDownloadInternal() success=$updated, threadDescriptor=$threadDescriptor")
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

    if (success) {
      _threadDownloadUpdateFlow.emit(Event.StartDownload(threadDescriptor))
    } else {
      mutex.withLock { threadDownloadsMap.remove(threadDescriptor) }
    }

    Logger.d(TAG, "startThreadDownloadInternal() threadDescriptor=$threadDescriptor, " +
      "downloadMedia=$downloadMedia, success=$success")
  }

  private fun ensureInitialized() {
    check(suspendableInitializer.isInitialized()) { "ThreadDownloadManager is not initialized yet! Use " }
  }

  sealed class Event {
    data class StartDownload(val threadDescriptor: ChanDescriptor.ThreadDescriptor) : Event()
    data class StopDownload(val threadDescriptor: ChanDescriptor.ThreadDescriptor) : Event()
    data class CompleteDownload(val threadDescriptor: ChanDescriptor.ThreadDescriptor) : Event()
    data class CancelDownload(val threadDescriptor: ChanDescriptor.ThreadDescriptor) : Event()
  }

  class UpdateData(
    val oldThreadDownload: ThreadDownload,
    val newThreadDownload: ThreadDownload
  )

  companion object {
    private const val TAG = "ThreadDownloadManager"
  }

}