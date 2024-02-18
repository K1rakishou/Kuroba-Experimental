package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class ReplyInfo(
  initialStatus: PostingStatus,
  initialReplyMode: ReplyMode,
  retrying: Boolean,
  val chanDescriptor: ChanDescriptor
) {
  private val _status = AtomicReference<PostingStatus>(initialStatus)
  private val _statusUpdates = MutableSharedFlow<PostingStatus>(extraBufferCapacity = Channel.UNLIMITED)
  private val _canceled = AtomicBoolean(false)
  private val _lastError = AtomicReference<ReplyResponse?>(null)

  val retrying: AtomicBoolean = AtomicBoolean(retrying)
  val replyModeRef: AtomicReference<ReplyMode> = AtomicReference(initialReplyMode)
  val enqueuedAt: AtomicLong = AtomicLong(System.currentTimeMillis())

  val lastUnsuccessfulReplyResponse: ReplyResponse?
    get() = _lastError.get()

  val currentStatus: PostingStatus
    get() = _status.get()
  val statusUpdates: SharedFlow<PostingStatus>
    get() = _statusUpdates.asSharedFlow()
  private val activeJob = AtomicReference<Job?>(null)

  val canceled: Boolean
    get() = activeJob.get() == null || _canceled.get()

  @Synchronized
  fun updateStatus(newStatus: PostingStatus) {
    Logger.d(TAG, "updateStatus(${chanDescriptor}) currentStatus: ${currentStatus}, newStatus: ${newStatus}")
    updateStatusInternal(newStatus)

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

    Logger.d(TAG, "cancelReplyUpload(${chanDescriptor}) cancelling, status=${currentStatus}")

    _canceled.set(true)
    activeJob.get()?.cancel()
    activeJob.set(null)
    _lastError.set(null)

    return true
  }

  @Synchronized
  fun reset(chanDescriptor: ChanDescriptor) {
    Logger.d(TAG, "reset(${chanDescriptor}) status=${currentStatus}")

    retrying.set(false)
    _canceled.set(false)
    activeJob.set(null)
    _lastError.set(null)
    enqueuedAt.set(System.currentTimeMillis())

    updateStatusInternal(PostingStatus.Enqueued(chanDescriptor))
  }

  @Synchronized
  fun setJob(job: Job) {
    Logger.d(TAG, "setJob(${chanDescriptor}) cancelling, status=${currentStatus}")

    val previousJob = activeJob.get()
    activeJob.set(job)
    previousJob?.cancel()
  }

  private fun updateStatusInternal(newStatus: PostingStatus) {
    val prevStatus = _status.get()
    _status.set(newStatus)

    if (prevStatus is PostingStatus.Attached && newStatus is PostingStatus.AfterPosting) {
      // Hack! Do not emit AfterPosting is previous status was Attached to avoid showing "Post canceled" dialog
      // when closing the reply notification.
      return
    }

    if (!_statusUpdates.tryEmit(newStatus)) {
      Logger.error(TAG) { "updateStatusInternal(${chanDescriptor}) failed to emit ${newStatus}" }
    }
  }

  fun needsUserAttention(): Boolean {
    val localCurrentStatus = currentStatus

    if (localCurrentStatus is PostingStatus.Attached) {
      return false
    }

    if (localCurrentStatus is PostingStatus.AfterPosting) {
      val postResult = localCurrentStatus.postResult

      if (postResult is PostResult.Success && postResult.replyResponse.posted) {
        return false
      }

      if (postResult is PostResult.Canceled) {
        return false
      }
    }

    return true
  }

  companion object {
    private const val TAG = "ReplyInfo"
  }

}