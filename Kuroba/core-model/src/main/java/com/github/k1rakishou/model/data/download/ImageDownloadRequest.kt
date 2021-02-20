package com.github.k1rakishou.model.data.download

import android.net.Uri
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import okhttp3.HttpUrl
import org.joda.time.DateTime

data class ImageDownloadRequest(
  val uniqueId: String,
  val imageServerFileName: String,
  val imageFullUrl: HttpUrl,
  val newFileName: String? = null,
  val status: Status = Status.Queued,
  val duplicatePathUri: Uri? = null,
  val duplicatesResolution: ImageSaverV2Options.DuplicatesResolution = ImageSaverV2Options.DuplicatesResolution.AskWhatToDo,
  val createdOn: DateTime = DateTime.now()
) {

  enum class Status(val rawValue: Int) {
    Queued(STATUS_QUEUED),
    Downloaded(STATUS_DOWNLOADED),
    ResolvingDuplicate(STATUS_RESOLVING_DUPLICATE),
    DownloadFailed(STATUS_DOWNLOAD_FAILED),
    Canceled(STATUS_CANCELED);

    companion object {
      fun fromRawValue(rawValue: Int): Status? {
        return values().firstOrNull { status -> status.rawValue == rawValue }
      }
    }
  }

  companion object {
    const val STATUS_QUEUED = 0
    const val STATUS_DOWNLOADED = 1
    const val STATUS_RESOLVING_DUPLICATE = 2
    const val STATUS_DOWNLOAD_FAILED = 3
    const val STATUS_CANCELED = 4

    const val DUPLICATE_RESOLUTION_ASK_USER = 0
    const val DUPLICATE_RESOLUTION_DOWNLOAD = 1
    const val DUPLICATE_RESOLUTION_SKIP = 2
  }
}