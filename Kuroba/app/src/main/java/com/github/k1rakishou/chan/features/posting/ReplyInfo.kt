package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class ReplyInfo(
  initialStatus: PostingStatus,
  private val chanDescriptor: ChanDescriptor,
  private val _status: MutableStateFlow<PostingStatus> = MutableStateFlow(initialStatus),
  val retrying: AtomicBoolean = AtomicBoolean(false),
  val enqueuedAt: AtomicLong = AtomicLong(System.currentTimeMillis())
) {
  private val _canceled = AtomicBoolean(false)

  val currentStatus: PostingStatus
    get() = _status.value
  val statusUpdates: StateFlow<PostingStatus>
    get() = _status.asStateFlow()
  val activeJob = AtomicReference<Job>(null)

  val canceled: Boolean
    get() = activeJob.get() == null || _canceled.get()

  @Synchronized
  fun updateStatus(newStatus: PostingStatus) {
    Logger.d("ReplyInfo", "updateStatus($chanDescriptor) -> ${newStatus.javaClass.simpleName}")
    _status.value = newStatus
  }

  @Synchronized
  fun cancelReplyUpload() {
    _canceled.set(true)

    val job = activeJob.get() ?: return
    job.cancel()

    activeJob.set(null)
  }

  @Synchronized
  fun reset(chanDescriptor: ChanDescriptor) {
    retrying.set(false)
    _canceled.set(false)
    activeJob.set(null)
    enqueuedAt.set(System.currentTimeMillis())

    _status.value = PostingStatus.Enqueued(chanDescriptor)
  }

}