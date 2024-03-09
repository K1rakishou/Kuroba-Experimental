package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.helper.OneShotRunnable
import com.github.k1rakishou.chan.core.helper.ThreadDownloaderFileManagerWrapper
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.ThreadDownloadRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.joda.time.DateTime
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadDownloadManager(
  private val appCostants: AppConstants,
  private val appScope: CoroutineScope,
  private val _threadDownloaderFileManagerWrapper: Lazy<ThreadDownloaderFileManagerWrapper>,
  private val _threadDownloadRepository: Lazy<ThreadDownloadRepository>,
  private val _chanPostRepository: Lazy<ChanPostRepository>
) {
  private val mutex = Mutex()

  private val threadDownloaderFileManagerWrapper: ThreadDownloaderFileManagerWrapper
    get() = _threadDownloaderFileManagerWrapper.get()
  private val threadDownloadRepository: ThreadDownloadRepository
    get() = _threadDownloadRepository.get()
  private val chanPostRepository: ChanPostRepository
    get() = _chanPostRepository.get()
  private val fileManager: FileManager
    get() = threadDownloaderFileManagerWrapper.fileManager

  @GuardedBy("mutex")
  private val threadDownloadsMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ThreadDownload>(16)

  private val initializationRunnable = OneShotRunnable()

  private val _threadDownloadUpdateFlow = MutableSharedFlow<Event>(extraBufferCapacity = 16)
  val threadDownloadUpdateFlow: SharedFlow<Event>
    get() = _threadDownloadUpdateFlow.asSharedFlow()

  private val _threadsProcessedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val threadsProcessedFlow: SharedFlow<Unit>
    get() = _threadsProcessedFlow.asSharedFlow()

  suspend fun getDownloadingThreadDescriptors(): Set<ChanDescriptor.ThreadDescriptor> {
    ensureInitialized()

    return mutex.withLock {
      threadDownloadsMap.values
        .filter { threadDownload -> threadDownload.status == ThreadDownload.Status.Running }
        .map { threadDownload -> threadDownload.threadDescriptor }
        .toSet()
    }
  }

  suspend fun getStatus(threadDescriptor: ChanDescriptor.ThreadDescriptor): ThreadDownload.Status? {
    ensureInitialized()

    return mutex.withLock { threadDownloadsMap[threadDescriptor]?.status }
  }

  suspend fun isThreadFullyDownloaded(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    ensureInitialized()

    return getStatus(threadDescriptor) == ThreadDownload.Status.Completed
  }

  suspend fun hasActiveThreads(): Boolean {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap.values.any { threadDownload -> threadDownload.status.isRunning() }
    }
  }

  suspend fun activeThreadsCount(): Int {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap.values.count { threadDownload -> threadDownload.status.isRunning() }
    }
  }

  suspend fun notCompletedThreadsCount(): Int {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap.values.count { threadDownload -> !threadDownload.status.isCompleted() }
    }
  }

  suspend fun getAllThreadDownloads(): List<ThreadDownload> {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap
        .values
        .map { threadDownload -> threadDownload.copy() }
    }
  }

  suspend fun getAllActiveThreadDownloads(): List<ThreadDownload> {
    ensureInitialized()

    return mutex.withLock {
      return@withLock threadDownloadsMap
        .values
        .filter { threadDownload -> threadDownload.status.isRunning() }
        .map { threadDownload -> threadDownload.copy() }
    }
  }

  suspend fun startDownloading(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    threadThumbnailUrl: String?,
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
      resumeThreadDownloadInternal(threadDescriptor, downloadMedia)
    } else {
      startThreadDownloadInternal(threadDescriptor, downloadMedia, threadThumbnailUrl)
    }
  }

  suspend fun resumeDownloading(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    ensureInitialized()

    val updated = updateThreadDownload(threadDescriptor, updaterFunc = { threadDownload ->
      if (threadDownload.status != ThreadDownload.Status.Stopped) {
        return@updateThreadDownload null
      }

      return@updateThreadDownload threadDownload.copy(status = ThreadDownload.Status.Running)
    })

    if (updated) {
      _threadDownloadUpdateFlow.emit(Event.StartDownload(threadDescriptor))
    }

    Logger.d(TAG, "stopDownloading() success=$updated, threadDescriptor=$threadDescriptor")
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

  suspend fun onDownloadProcessed(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    resultMessage: String?
  ) {
    ensureInitialized()

    updateThreadDownload(threadDescriptor, updaterFunc = { threadDownload ->
      val updateTime = DateTime.now()

      return@updateThreadDownload threadDownload.copy(
        lastUpdateTime = updateTime,
        downloadResultMsg = resultMessage
      )
    })

    Logger.d(TAG, "onDownloadProcessed() threadDescriptor=$threadDescriptor")
  }

  suspend fun cancelDownloads(threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>) {
    ensureInitialized()

    val removedThreadDownloads = mutex.withLock {
      val removed = mutableListOf<ThreadDownload>()

      threadDescriptors.forEach { threadDescriptor ->
        val removeDownload = threadDownloadsMap.remove(threadDescriptor)
        if (removeDownload != null) {
          removed += removeDownload
        }
      }

      return@withLock removed
    }

    if (removedThreadDownloads.isEmpty()) {
      Logger.d(TAG, "cancelDownloads() nothing to cancel")
      return
    }

    threadDownloadRepository.deleteThreadDownload(removedThreadDownloads)
      .safeUnwrap { error ->
        Logger.e(TAG, "cancelDownloading() Failed to delete thread download from the DB", error)

        mutex.withLock {
          removedThreadDownloads.forEach { threadDownload ->
            threadDownloadsMap[threadDownload.threadDescriptor] = threadDownload
          }
        }

        return
      }

    removedThreadDownloads.forEach { threadDownload ->
      chanPostRepository.deleteThread(threadDownload.threadDescriptor)
        .onError { error -> Logger.e(TAG, "deleteThread(${threadDownload.threadDescriptor}) error", error) }
        .ignore()

      val threadDownloaderCacheDir = fileManager.fromRawFile(appCostants.threadDownloaderCacheDir)
      val threadDirName = ThreadDownloadingDelegate.formatDirectoryName(threadDownload.threadDescriptor)

      val resultDirectory = threadDownloaderCacheDir
        .clone(DirectorySegment(threadDirName))

      fileManager.delete(resultDirectory)
    }

    threadDescriptors.forEach { threadDescriptor ->
      _threadDownloadUpdateFlow.emit(Event.CancelDownload(threadDescriptor))
      Logger.d(TAG, "cancelDownloading() success, threadDescriptor=$threadDescriptor")
    }
  }

  suspend fun onThreadsProcessed() {
    ensureInitialized()

    _threadsProcessedFlow.emit(Unit)
  }

  suspend fun updateThreadDownload(
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

  @OptIn(ExperimentalTime::class)
  suspend fun findDownloadedFile(
    httpUrl: HttpUrl,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): AbstractFile? {
    ensureInitialized()

    val canUseThreadDownloaderCache = canUseThreadDownloaderCache(threadDescriptor)
    if (!canUseThreadDownloaderCache) {
      return null
    }

    val fileName = httpUrl.extractFileName()
    if (fileName == null) {
      return null
    }

    return withContext(Dispatchers.IO) {
      val threadDownloaderCacheDir = fileManager.fromRawFile(appCostants.threadDownloaderCacheDir)

      val resultDirectory = threadDownloaderCacheDir
        .clone(DirectorySegment(ThreadDownloadingDelegate.formatDirectoryName(threadDescriptor)))

      return@withContext fileManager.findFile(resultDirectory, fileName)
    }
  }

  suspend fun canUseThreadDownloaderCache(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    ensureInitialized()

    return mutex.withLock {
      val statusIsGood = threadDownloadsMap[threadDescriptor]?.status != null
      val wasUpdatedAtLeastOnce = threadDownloadsMap[threadDescriptor]?.lastUpdateTime != null
      val downloadMedia = threadDownloadsMap[threadDescriptor]?.downloadMedia != false

      return@withLock downloadMedia && statusIsGood && wasUpdatedAtLeastOnce
    }
  }

  private suspend fun resumeThreadDownloadInternal(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    downloadMedia: Boolean
  ) {
    val updated = updateThreadDownload(threadDescriptor, updaterFunc = { threadDownload ->
      if (threadDownload.status != ThreadDownload.Status.Stopped) {
        return@updateThreadDownload null
      }

      return@updateThreadDownload threadDownload.copy(
        status = ThreadDownload.Status.Running,
        downloadMedia = downloadMedia
      )
    })

    if (updated) {
      _threadDownloadUpdateFlow.emit(Event.StartDownload(threadDescriptor))
    }

    Logger.d(TAG, "resumeThreadDownloadInternal() success=$updated, threadDescriptor=$threadDescriptor")
  }

  private suspend fun startThreadDownloadInternal(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    downloadMedia: Boolean,
    threadThumbnailUrl: String?
  ) {
    var success = false

    val databaseId = chanPostRepository.createEmptyThreadIfNotExists(threadDescriptor)
      .onError { error -> Logger.e(TAG, "Failed to create empty thread in the DB", error) }
      .valueOrNull() ?: -1L

    if (databaseId >= 0L) {
      val threadDownload = ThreadDownload(
        ownerThreadDatabaseId = databaseId,
        threadDescriptor = threadDescriptor,
        downloadMedia = downloadMedia,
        status = ThreadDownload.Status.Running,
        createdOn = DateTime.now(),
        threadThumbnailUrl = threadThumbnailUrl,
        lastUpdateTime = null,
        downloadResultMsg = null
      )

      val threadDownloadCreated = threadDownloadRepository.createThreadDownload(threadDownload)
        .onError { error -> Logger.e(TAG, "Failed to create thread download in the DB", error) }
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

  private suspend fun ensureInitialized() {
    initializationRunnable.runIfNotYet { initializeThreadDownloadManagerInternal() }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun initializeThreadDownloadManagerInternal() {
    withContext(Dispatchers.IO) {
      Logger.d(TAG, "initializeThreadDownloadManagerInternal() start")

      val time = measureTime {
        val initResult = threadDownloadRepository.initialize()
        if (initResult is ModularResult.Value) {
          val threadDownloads = initResult.value

          mutex.withLock {
            threadDownloads.forEach { threadDownload ->
              threadDownloadsMap[threadDownload.threadDescriptor] = threadDownload
            }
          }
        }
      }

      Logger.d(TAG, "initializeThreadDownloadManagerInternal() end, took $time")
      _threadDownloadUpdateFlow.emit(Event.Initialized)
    }
  }

  sealed class Event {
    data object Initialized : Event()
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