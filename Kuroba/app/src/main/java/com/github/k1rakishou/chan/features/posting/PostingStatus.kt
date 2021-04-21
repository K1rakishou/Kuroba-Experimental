package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

sealed class PostingStatus {
  abstract val chanDescriptor: ChanDescriptor

  fun isActive(): Boolean {
    return when (this) {
      is AfterPosting,
      is Attached -> false
      is WaitingForSiteRateLimitToPass,
      is WaitingForAdditionalService,
      is BeforePosting,
      is Enqueued,
      is Progress -> true
    }
  }

  fun isTerminalEvent(): Boolean {
    return this is AfterPosting
  }

  data class Attached(
    override val chanDescriptor: ChanDescriptor
  ) : PostingStatus()

  data class Enqueued(
    override val chanDescriptor: ChanDescriptor
  ) : PostingStatus()

  data class WaitingForAdditionalService(
    val serviceName: String,
    override val chanDescriptor: ChanDescriptor
  ) : PostingStatus()

  data class WaitingForSiteRateLimitToPass(
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
  ) : PostingStatus() {
    fun toOverallPercent(): Float {
      val index = (fileIndex - 1).coerceAtLeast(0).toFloat()

      val totalProgress = totalFiles.toFloat() * 100f
      val currentProgress = (index * 100f) + percent.toFloat()

      return currentProgress / totalProgress
    }
  }

  data class AfterPosting(
    override val chanDescriptor: ChanDescriptor,
    val postResult: PostResult
  ) : PostingStatus()
}