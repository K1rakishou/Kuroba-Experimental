package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class ReplyInfo(
  initialStatus: PostingStatus,
  initialReplyMode: ReplyMode,
  val chanDescriptor: ChanDescriptor,
  private val _status: MutableStateFlow<PostingStatus> = MutableStateFlow(initialStatus),
  val retrying: AtomicBoolean = AtomicBoolean(false),
  val enqueuedAt: AtomicLong = AtomicLong(System.currentTimeMillis()),
  val replyModeRef: AtomicReference<ReplyMode> = AtomicReference(initialReplyMode)
) {
  private val _canceled = AtomicBoolean(false)
  private val _lastError = AtomicReference<ReplyResponse?>(null)

  val lastUnsuccessfulReplyResponse: ReplyResponse?
    get() = _lastError.get()

  val currentStatus: PostingStatus
    get() = _status.value
  val statusUpdates: StateFlow<PostingStatus>
    get() = _status.asStateFlow()
  private val activeJob = AtomicReference<Job>(null)

  val canceled: Boolean
    get() = activeJob.get() == null || _canceled.get()

  @Synchronized
  fun updateStatus(newStatus: PostingStatus) {
    _status.value = newStatus

    if (newStatus is PostingStatus.AfterPosting) {
      val postResult = newStatus.postResult
      if (postResult is PostResult.Error) {
        val replyResponse = ReplyResponse()
        replyResponse.errorMessage = postResult.throwable.errorMessageOrClassName()

        _lastError.set(replyResponse)
      } else if (postResult is PostResult.Success && !postResult.replyResponse.posted) {
        val replyResponse = ReplyResponse(ReplyResponse(postResult.replyResponse))
        _lastError.set(replyResponse)
      } else {
        _lastError.set(null)
      }
    }
  }

  @Synchronized
  fun cancelReplyUpload(): Boolean {
    if (!currentStatus.canCancel()) {
      Logger.d(TAG, "cancelReplyUpload() can't cancel, status=${currentStatus}")
      return false
    }

    Logger.d(TAG, "cancelReplyUpload() cancelling, status=${currentStatus}")

    _canceled.set(true)
    activeJob.get()?.cancel()
    activeJob.set(null)
    _lastError.set(null)

    _status.value = PostingStatus.Attached(chanDescriptor)

    return true
  }

  @Synchronized
  fun reset(chanDescriptor: ChanDescriptor) {
    retrying.set(false)
    _canceled.set(false)
    activeJob.set(null)
    _lastError.set(null)
    enqueuedAt.set(System.currentTimeMillis())

    _status.value = PostingStatus.Enqueued(chanDescriptor)
  }

  @Synchronized
  fun setJob(job: Job) {
    if (!activeJob.compareAndSet(null, job)) {
      job.cancel()
    }
  }

  companion object {
    private const val TAG = "ReplyInfo"
  }

}