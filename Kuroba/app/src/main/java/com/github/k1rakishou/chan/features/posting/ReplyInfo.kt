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
  private val activeJob = AtomicReference<Job?>(null)

  val retrying: AtomicBoolean = AtomicBoolean(retrying)
  val replyModeRef: AtomicReference<ReplyMode> = AtomicReference(initialReplyMode)
  val enqueuedAt: AtomicLong = AtomicLong(System.currentTimeMillis())

  private val _lastError = AtomicReference<ReplyResponse?>(null)
  val lastUnsuccessfulReplyResponse: ReplyResponse?
    get() = _lastError.get()

  private val _status = AtomicReference<PostingStatus>(initialStatus)
  val currentStatus: PostingStatus
    get() = _status.get()

  private val _statusUpdates = MutableSharedFlow<PostingStatus>(extraBufferCapacity = Channel.UNLIMITED)
  val statusUpdates: SharedFlow<PostingStatus>
    get() = _statusUpdates.asSharedFlow()

  private val _canceled = AtomicBoolean(false)
  val canceled: Boolean
    get() = activeJob.get() == null || _canceled.get()

  @Synchronized
  fun updateStatus(newStatus: PostingStatus) {
    val prevStatus = _status.get()
    updateStatusInternal(prevStatus, newStatus)

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

    _status.set(newStatus)
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
    Logger.d(TAG, "reset(${chanDescriptor}) currentStatus: ${currentStatus}")

    retrying.set(false)
    activeJob.set(null)
    _canceled.set(false)
    _lastError.set(null)
    enqueuedAt.set(System.currentTimeMillis())

    updateStatusInternal(currentStatus, PostingStatus.Enqueued(chanDescriptor))
  }

  @Synchronized
  fun setJob(job: Job) {
    Logger.d(TAG, "setJob(${chanDescriptor}) cancelling, status=${currentStatus}")

    val previousJob = activeJob.get()
    activeJob.set(job)
    previousJob?.cancel()
  }

  private fun updateStatusInternal(prevStatus: PostingStatus, newStatus: PostingStatus) {
    if (prevStatus is PostingStatus.Attached && newStatus is PostingStatus.AfterPosting) {
      // Hack! Do not emit AfterPosting if previous status was Attached to avoid showing "Post canceled" dialog
      // when closing the reply notification.
      Logger.verbose(TAG) {
        "updateStatusInternal(${chanDescriptor}) skipping event emission " +
          "because prevStatus is PostingStatus.Attached (${prevStatus}) " +
          "and newStatus is PostingStatus.AfterPosting (${newStatus})"
      }

      return
    }

    if (_statusUpdates.tryEmit(newStatus)) {
      Logger.debug(TAG) { "updateStatusInternal(${chanDescriptor}) emitting ${newStatus} (prevStatus: ${prevStatus})" }
    } else {
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