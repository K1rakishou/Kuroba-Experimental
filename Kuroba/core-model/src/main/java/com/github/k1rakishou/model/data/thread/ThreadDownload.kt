package com.github.k1rakishou.model.data.thread

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import org.joda.time.DateTime

data class ThreadDownload(
  // Database id of a thread we are downloading
  val ownerThreadDatabaseId: Long,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val downloadMedia: Boolean,
  var status: Status = Status.Running,
  var createdOn: DateTime,
  var threadThumbnailUrl: String?,
  var lastUpdateTime: DateTime?,
  var downloadResultMsg: String?
) {

  enum class Status(val rawValue: Int) {
    Running(1),
    Stopped(2),
    Completed(3);

    fun isRunning(): Boolean {
      return this == Running
    }

    fun isCompleted(): Boolean {
      return this == Completed
    }

    companion object {
      fun fromRawValue(rawValue: Int): Status {
        return values().firstOrNull { it.rawValue == rawValue } ?: Running
      }
    }
  }

  companion object {
    val DEFAULT_THREAD_DOWNLOAD = ThreadDownload(
      ownerThreadDatabaseId = -1,
      threadDescriptor = ChanDescriptor.ThreadDescriptor.create("", "", Long.MAX_VALUE),
      downloadMedia = false,
      status = Status.Running,
      createdOn = DateTime.now(),
      threadThumbnailUrl = null,
      lastUpdateTime = null,
      downloadResultMsg = null
    )
  }
}