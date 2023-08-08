package com.github.k1rakishou.chan.features.posting

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class MainNotificationInfo(val activeRepliesCount: Int)

data class ChildNotificationInfo(
  val chanDescriptor: ChanDescriptor,
  val status: Status
) {

  val canCancel: Boolean
    get() {
      return when (status) {
        is Status.WaitingForSiteRateLimitToPass,
        is Status.WaitingForAdditionalService,
        is Status.Preparing -> true
        is Status.Uploading -> status.totalProgress < .9f
        is Status.Uploaded,
        is Status.Error,
        is Status.Posted,
        Status.Canceled -> false
      }
    }

  val isOngoing: Boolean
    get() {
      return when (status) {
        is Status.Uploading,
        is Status.Uploaded,
        is Status.WaitingForSiteRateLimitToPass,
        is Status.WaitingForAdditionalService -> true
        is Status.Error,
        is Status.Preparing,
        is Status.Posted,
        Status.Canceled -> false
      }
    }

  sealed class Status(
    val statusText: String
  ) {
    class Preparing(
      chanDescriptor: ChanDescriptor
    ) : Status("Preparing to send a reply to ${chanDescriptor.userReadableString()}")

    data class WaitingForSiteRateLimitToPass(
      val remainingWaitTimeMs: Long,
      val boardDescriptor: BoardDescriptor,
    ) : Status("Waiting ${remainingWaitTimeMs / 1000}s until ${boardDescriptor.siteName()}/${boardDescriptor.boardCode} rate limit is over")

    data class WaitingForAdditionalService(
      val availableAttempts: Int,
      val serviceName: String
    ) : Status("Waiting for external service: \"${serviceName}\" (attempts: $availableAttempts)")

    data class Uploading(
      val totalProgress: Float
    ) : Status("Uploading ${(totalProgress * 100f).toInt()}%...")

    class Uploaded : Status("Uploaded") {
      override fun toString(): String {
        return "Uploaded"
      }
    }

    data class Posted(
      val chanDescriptor: ChanDescriptor
    ) : Status("Posted in ${chanDescriptor.userReadableString()}")

    data class Error(
      val errorMessage: CharSequence
    ) : Status("Failed to post, error=${errorMessage}")

    data object Canceled : Status("Canceled")
  }

  companion object {
    fun default(chanDescriptor: ChanDescriptor): ChildNotificationInfo {
      return ChildNotificationInfo(chanDescriptor, Status.Preparing(chanDescriptor))
    }
  }
}