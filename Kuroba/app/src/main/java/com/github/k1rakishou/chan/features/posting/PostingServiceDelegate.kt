package com.github.k1rakishou.chan.features.posting

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.LastReplyRepository
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.ui.helper.PostHelper
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PostingServiceDelegate(
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val replyManager: ReplyManager,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val bookmarksManager: BookmarksManager,
  private val savedReplyManager: SavedReplyManager,
  private val chanThreadManager: ChanThreadManager,
  private val lastReplyRepository: LastReplyRepository,
  private val chanPostRepository: ChanPostRepository
) {
  private val mutex = Mutex()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  @GuardedBy("mutex")
  private val activeReplyDescriptors = hashMapOf<ChanDescriptor, ReplyInfo>()

  private val _stopServiceEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val _updateMainNotificationFlow = MutableSharedFlow<MainNotificationInfo>(extraBufferCapacity = 1)

  private val channel = appScope.actor<ChanDescriptor>(capacity = Channel.UNLIMITED) {
    consumeEach { chanDescriptor ->
      val postedSuccessfully = AtomicBoolean(false)

      val job = supervisorScope {
        return@supervisorScope appScope.launch {
          processNewReply(chanDescriptor, postedSuccessfully)
            .catch { error ->
              Logger.e(TAG, "Unhandled exception", error)
              doWithReplyInfo(chanDescriptor) {
                updateStatus(PostingStatus.AfterPosting(chanDescriptor, PostResult.Error(error)))
              }
            }
            .collect { status ->
              doWithReplyInfo(chanDescriptor) { updateStatus(status) }

              when (status) {
                is PostingStatus.Attached,
                is PostingStatus.Enqueued,
                is PostingStatus.BeforePosting,
                is PostingStatus.Progress -> {
                  Logger.d(TAG, "processNewReply($chanDescriptor) -> ${status.javaClass.simpleName}")
                }
                is PostingStatus.AfterPosting -> {
                  when (status.postResult) {
                    PostResult.Canceled -> {
                      Logger.e(TAG, "processNewReply($chanDescriptor) AfterPosting canceled")
                    }
                    is PostResult.Error -> {
                      Logger.e(TAG, "processNewReply($chanDescriptor) AfterPosting error message: ${status.postResult.throwable}")
                    }
                    is PostResult.Success -> {
                      Logger.e(TAG, "processNewReply($chanDescriptor) AfterPosting success")
                    }
                  }
                }
              }
            }
        }
      }

      doWithReplyInfo(chanDescriptor) {
        activeJob.compareAndSet(null, job)
      }

      try {
        job.join()
      } catch (error: Throwable) {
        if (error is CancellationException) {
          doWithReplyInfo(chanDescriptor) {
            updateStatus(PostingStatus.AfterPosting(chanDescriptor = chanDescriptor, postResult = PostResult.Canceled))
          }
        } else {
          doWithReplyInfo(chanDescriptor) {
            updateStatus(PostingStatus.AfterPosting(chanDescriptor = chanDescriptor, postResult = PostResult.Error(error)))
          }
        }
      }

      if (job.isCancelled) {
        doWithReplyInfo(chanDescriptor) {
          updateStatus(PostingStatus.AfterPosting(chanDescriptor = chanDescriptor, postResult = PostResult.Canceled))
        }
      }

      if (!postedSuccessfully.get()) {
        replyManager.restoreFiles(chanDescriptor)
      }

      updateMainNotification()

      val allRepliesProcessed = mutex.withLock {
        if (activeReplyDescriptors.isEmpty()) {
          return@withLock true
        }

        return@withLock activeReplyDescriptors.values.all { replyInfo ->
          return@all replyInfo.currentStatus is PostingStatus.Attached
            || replyInfo.currentStatus.isTerminalEvent()
        }
      }

      if (allRepliesProcessed) {
        Logger.d(TAG, "All replies processed, stopping the service")
        _stopServiceEventFlow.emit(Unit)
      }
    }
  }

  fun listenForStopServiceEvents(): SharedFlow<Unit> {
    return _stopServiceEventFlow
  }

  fun listenForMainNotificationUpdates(): SharedFlow<MainNotificationInfo> {
    return _updateMainNotificationFlow
  }

  suspend fun listenForPostingStatusUpdates(chanDescriptor: ChanDescriptor): StateFlow<PostingStatus> {
    Logger.d(TAG, "listenForPostingStatusUpdates($chanDescriptor)")

    return mutex.withLock {
      if (activeReplyDescriptors.containsKey(chanDescriptor)) {
        return@withLock activeReplyDescriptors[chanDescriptor]!!.statusUpdates
      }

      activeReplyDescriptors.put(
        chanDescriptor,
        ReplyInfo(initialStatus = PostingStatus.Attached(chanDescriptor))
      )

      return@withLock activeReplyDescriptors[chanDescriptor]!!.statusUpdates
    }
  }

  fun onNewReply(chanDescriptor: ChanDescriptor, retrying: Boolean) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      val needActorKick = mutex.withLock { addOrUpdateReplyInfo(chanDescriptor, retrying) }
      if (!needActorKick) {
        return@post
      }

      val result = awaitUntilEverythingIsInitialized(chanDescriptor)
      if (result is ModularResult.Error) {
        Logger.e(TAG, "awaitUntilEverythingIsInitialized($chanDescriptor) error", result.error)
        return@post
      }

      Logger.d(TAG, "onNewReply($chanDescriptor)")

      updateMainNotification()
      channel.send(chanDescriptor)
    }
  }

  private fun addOrUpdateReplyInfo(chanDescriptor: ChanDescriptor, retrying: Boolean): Boolean {
    if (activeReplyDescriptors.containsKey(chanDescriptor)) {
      val replyInfo = activeReplyDescriptors[chanDescriptor]!!

      val needActorKick = when (replyInfo.currentStatus) {
        is PostingStatus.Attached,
        is PostingStatus.AfterPosting -> true
        is PostingStatus.Enqueued,
        is PostingStatus.Progress,
        is PostingStatus.BeforePosting -> false
      }

      if (replyInfo.currentStatus is PostingStatus.Attached) {
        replyInfo.updateStatus(PostingStatus.Enqueued(chanDescriptor))
      }

      return needActorKick
    }

    activeReplyDescriptors[chanDescriptor] = ReplyInfo(
      initialStatus = PostingStatus.Enqueued(chanDescriptor),
      retrying = AtomicBoolean(retrying)
    )

    return true
  }

  private suspend fun updateMainNotification() {
    val activeRepliesCount = mutex.withLock {
      return@withLock activeReplyDescriptors.values
        .count { replyInfo -> replyInfo.currentStatus.isActive() }
    }

    _updateMainNotificationFlow.emit(MainNotificationInfo(activeRepliesCount))
  }

  private suspend fun processNewReply(
    chanDescriptor: ChanDescriptor,
    postedSuccessfully: AtomicBoolean
  ): Flow<PostingStatus> {
    return flow {
      BackgroundUtils.ensureMainThread()

      val reply = replyManager.getReplyOrNull(chanDescriptor)
      if (reply == null) {
        Logger.e(TAG, "No reply found for chanDescriptor: $chanDescriptor")
        emitTerminalEvent(
          chanDescriptor = chanDescriptor,
          postResult = PostResult.Error(IOException("Canceled"))
        )
        return@flow
      }

      val retrying = doWithReplyInfo(chanDescriptor) { retrying.get() } ?: false
      makeSubmitCall(chanDescriptor, postedSuccessfully, retrying)
    }
  }

  private suspend fun FlowCollector<PostingStatus>.makeSubmitCall(
    chanDescriptor: ChanDescriptor,
    postedSuccessfully: AtomicBoolean,
    retrying: Boolean
  ) {
    Logger.d(TAG, "makeSubmitCall() chanDescriptor=${chanDescriptor}")

    val siteDescriptor = chanDescriptor.siteDescriptor()
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())

    if (site == null) {
      Logger.e(TAG, "makeSubmitCall() siteManager.bySiteDescriptor($siteDescriptor) -> null")

      emitTerminalEvent(
        chanDescriptor = chanDescriptor,
        postResult = PostResult.Error(
          throwable = PostingException("Failed to resolve site by site descriptor ${siteDescriptor}")
        )
      )

      return
    }

    val takeFilesResult = replyManager.takeSelectedFiles(chanDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "makeSubmitCall() takeSelectedFiles(${chanDescriptor}) error")
        emitTerminalEvent(chanDescriptor, PostResult.Error(error))
        return
      }

    if (!takeFilesResult) {
      emitTerminalEvent(
        chanDescriptor = chanDescriptor,
        postResult = PostResult.Error(
          throwable = IOException("Failed to move attached files into reply")
        )
      )
      return
    }

    emit(PostingStatus.BeforePosting(chanDescriptor))

    makePostInternal(
      site = site,
      chanDescriptor = chanDescriptor,
      retrying = retrying,
      postedSuccessfully = postedSuccessfully
    )
  }

  private suspend fun FlowCollector<PostingStatus>.makePostInternal(
    site: Site,
    chanDescriptor: ChanDescriptor,
    retrying: Boolean,
    postedSuccessfully: AtomicBoolean
  ) {
    site.actions()
      .post(chanDescriptor)
      .catch { error ->
        emitTerminalEvent(chanDescriptor, PostResult.Error(error))
      }
      .collect { postResult ->
        when (postResult) {
          is SiteActions.PostResult.UploadingProgress -> {
            val status = PostingStatus.Progress(
              chanDescriptor = chanDescriptor,
              fileIndex = postResult.fileIndex,
              totalFiles = postResult.totalFiles,
              percent = postResult.percent
            )

            emit(status)
          }
          is SiteActions.PostResult.PostError -> {
            emitTerminalEvent(chanDescriptor, PostResult.Error(postResult.error))
          }
          is SiteActions.PostResult.PostComplete -> {
            if (postResult.replyResponse.posted) {
              try {
                postedSuccessfully.set(true)
                onPostedSuccessfully(chanDescriptor, postResult.replyResponse)
              } catch (error: Throwable) {
                Logger.e(TAG, " onPostedSuccessfully($chanDescriptor) error", error)

                emitTerminalEvent(chanDescriptor, PostResult.Error(error))
                return@collect
              }
            }

            emitTerminalEvent(
              chanDescriptor = chanDescriptor,
              postResult = PostResult.Success(
                replyResponse = postResult.replyResponse,
                retrying = retrying
              )
            )
          }
        }
      }
  }

  private suspend fun FlowCollector<PostingStatus>.emitTerminalEvent(
    chanDescriptor: ChanDescriptor,
    postResult: PostResult
  ) {
    emit(PostingStatus.AfterPosting(chanDescriptor, postResult))
  }

  private suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ) {
    val siteDescriptor = replyResponse.siteDescriptor
    replyManager.cleanupFiles(prevChanDescriptor, notifyListeners = true)

    if (siteDescriptor == null) {
      Logger.e(TAG, "onPostedSuccessfully() siteDescriptor==null")
      return
    }

    // if the thread being presented has changed in the time waiting for this call to
    // complete, the loadable field in ReplyPresenter will be incorrect; reconstruct
    // the loadable (local to this method) from the reply response
    val localSite = siteManager.bySiteDescriptor(siteDescriptor)
    if (localSite == null) {
      Logger.e(TAG, "onPostedSuccessfully() localSite==null")
      return
    }

    val boardDescriptor = BoardDescriptor.create(siteDescriptor, replyResponse.boardCode)

    val localBoard = boardManager.byBoardDescriptor(boardDescriptor)
    if (localBoard == null) {
      Logger.e(TAG, "onPostedSuccessfully() localBoard==null")
      return
    }

    val threadNo = if (replyResponse.threadNo <= 0L) {
      replyResponse.postNo
    } else {
      replyResponse.threadNo
    }

    val newThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      localSite.name(),
      localBoard.boardCode(),
      threadNo
    )

    lastReplyRepository.putLastReply(newThreadDescriptor.boardDescriptor)

    if (prevChanDescriptor.isCatalogDescriptor()) {
      lastReplyRepository.putLastThread(prevChanDescriptor.boardDescriptor())
    }

    val createThreadSuccess = chanPostRepository.createEmptyThreadIfNotExists(newThreadDescriptor)
      .peekError { error ->
        Logger.e(TAG, "Failed to create empty thread in the database for $newThreadDescriptor", error)
      }
      .valueOrNull() == true

    if (createThreadSuccess && ChanSettings.postPinThread.get()) {
      bookmarkThread(newThreadDescriptor, threadNo)
    }

    val responsePostDescriptor = replyResponse.postDescriptorOrNull
    if (responsePostDescriptor != null) {
      val password = if (replyResponse.password.isNotEmpty()) {
        replyResponse.password
      } else {
        null
      }

      savedReplyManager.saveReply(ChanSavedReply(responsePostDescriptor, password))
    } else {
      Logger.e(TAG, "Couldn't create responsePostDescriptor, replyResponse=${replyResponse}")
    }

    replyManager.readReply(newThreadDescriptor) { newReply ->
      replyManager.readReply(prevChanDescriptor) { prevReply ->
        val prevName = prevReply.postName
        val prevFlag = prevReply.flag

        newReply.resetAfterPosting()
        newReply.postName = prevName
        newReply.flag = prevFlag
      }
    }
  }

  private fun bookmarkThread(
    newThreadDescriptor: ChanDescriptor,
    threadNo: Long
  ) {
    if (newThreadDescriptor is ChanDescriptor.ThreadDescriptor) {
      val thread = chanThreadManager.getChanThread(newThreadDescriptor)
      val bookmarkThreadDescriptor = newThreadDescriptor.toThreadDescriptor(threadNo)

      // reply
      val createBookmarkResult = if (thread != null) {
        val originalPost = thread.getOriginalPost()
        val title = ChanPostUtils.getTitle(originalPost, newThreadDescriptor)
        val thumbnail = originalPost.firstImage()?.actualThumbnailUrl

        bookmarksManager.createBookmark(bookmarkThreadDescriptor, title, thumbnail)
      } else {
        bookmarksManager.createBookmark(bookmarkThreadDescriptor)
      }

      if (!createBookmarkResult) {
        Logger.e(TAG, "bookmarkThread() Failed to create bookmark with newThreadDescriptor=$newThreadDescriptor, " +
          "threadDescriptor: $bookmarkThreadDescriptor, newThreadDescriptor=$newThreadDescriptor, " +
          "threadNo=$threadNo")
      }

      return
    }

    val title = replyManager.readReply(newThreadDescriptor) { reply ->
      PostHelper.getTitle(reply)
    }

    val bookmarkThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      boardDescriptor = newThreadDescriptor.boardDescriptor(),
      threadNo = threadNo
    )

    val createBookmarkResult = bookmarksManager.createBookmark(
      bookmarkThreadDescriptor,
      title
    )

    if (!createBookmarkResult) {
      Logger.e(TAG, "bookmarkThread() Failed to create bookmark with newThreadDescriptor=$newThreadDescriptor, " +
        "threadDescriptor: $bookmarkThreadDescriptor, newThreadDescriptor=$newThreadDescriptor, " +
        "threadNo=$threadNo")
    }
  }

  private suspend fun awaitUntilEverythingIsInitialized(chanDescriptor: ChanDescriptor): ModularResult<Unit> {
    return ModularResult.Try {
      withContext(Dispatchers.IO) {
        replyManager.reloadFilesFromDisk(appConstants).unwrap()

        if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
          savedReplyManager.preloadForThread(chanDescriptor)
        }
      }

      replyManager.awaitUntilFilesAreLoaded()
      siteManager.awaitUntilInitialized()
      boardManager.awaitUntilInitialized()
      chanPostRepository.awaitUntilInitialized()
      bookmarksManager.awaitUntilInitialized()
      chanThreadManager.awaitUntilDependenciesInitialized()
    }
  }

  private suspend fun <T : Any?> doWithReplyInfo(
    replyDescriptor: ChanDescriptor,
    func: ReplyInfo.() -> T
  ): T? {
    return mutex.withLock {
      activeReplyDescriptors[replyDescriptor]?.let { replyInfo -> func(replyInfo) }
    }
  }

  suspend fun isReplyCurrentlyInProgress(replyDescriptor: ChanDescriptor): Boolean {
    return mutex.withLock {
      val isReplyUploadActive = activeReplyDescriptors[replyDescriptor]
        ?.currentStatus
        ?.isActive()
        ?: false

      return@withLock isReplyUploadActive.not()
    }
  }

  suspend fun cancelReplySend(replyDescriptor: ChanDescriptor) {
    mutex.withLock { activeReplyDescriptors[replyDescriptor]?.cancelReplyUpload() }
  }

  class ReplyInfo(
    initialStatus: PostingStatus,
    private var _status: MutableStateFlow<PostingStatus> = MutableStateFlow(initialStatus),
    val retrying: AtomicBoolean = AtomicBoolean(false)
  ) {
    val currentStatus: PostingStatus
      get() = _status.value
    val statusUpdates: StateFlow<PostingStatus>
      get() = _status.asStateFlow()
    val activeJob = AtomicReference<Job>(null)

    @Synchronized
    fun updateStatus(newStatus: PostingStatus) {
      _status.value = newStatus
      Logger.d(TAG, "updateStatus, newStatus=${_status.value}")
    }

    @Synchronized
    fun cancelReplyUpload() {
      val job = activeJob.get() ?: return
      job.cancel()

      activeJob.set(null)
    }

  }

  sealed class PostResult {
    object Canceled : PostResult()

    data class Error(
      val throwable: Throwable
    ) : PostResult()

    data class Success(
      val replyResponse: ReplyResponse,
      val retrying: Boolean
    ) : PostResult()
  }

  sealed class PostingStatus {
    abstract val chanDescriptor: ChanDescriptor

    fun isActive(): Boolean {
      return when (this) {
        is AfterPosting,
        is Attached -> false
        is BeforePosting,
        is Enqueued,
        is Progress -> true
      }
    }

    fun isTerminalEvent(): Boolean {
      return when (this) {
        is Attached,
        is Enqueued,
        is Progress,
        is BeforePosting -> false
        is AfterPosting -> true
      }
    }

    data class Attached(
      override val chanDescriptor: ChanDescriptor
    ) : PostingStatus()

    data class Enqueued(
      override val chanDescriptor: ChanDescriptor
    ) : PostingStatus()

    data class BeforePosting(
      override val chanDescriptor: ChanDescriptor
    ) : PostingStatus()

    data class Progress(
      override val chanDescriptor: ChanDescriptor,
      val fileIndex: Int,
      val totalFiles: Int,
      val percent: Int
    ) : PostingStatus()

    data class AfterPosting(
      override val chanDescriptor: ChanDescriptor,
      val postResult: PostResult
    ) : PostingStatus()
  }

  data class MainNotificationInfo(val activeRepliesCount: Int)

  class PostingException(message: String) : Exception(message)

  companion object {
    private const val TAG = "PostingServiceDelegate"
  }

}