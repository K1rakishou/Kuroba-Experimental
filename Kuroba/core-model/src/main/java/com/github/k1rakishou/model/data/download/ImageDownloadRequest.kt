package com.github.k1rakishou.model.data.download

import android.net.Uri
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import okhttp3.HttpUrl
import org.joda.time.DateTime

data class ImageDownloadRequest(
  val uniqueId: String,
  val imageFullUrl: HttpUrl,
  val postDescriptorString: String,
  val newFileName: String? = null,
  val status: Status = Status.Queued,
  val duplicateFileUri: Uri? = null,
  val duplicatesResolution: ImageSaverV2Options.DuplicatesResolution = ImageSaverV2Options.DuplicatesResolution.AskWhatToDo,
  val createdOn: DateTime = DateTime.now()
) {

  enum class Status(val rawValue: Int) {
    Queued(0),
    Downloaded(1),
    ResolvingDuplicate(2),
    DownloadFailed(3),
    Canceled(4);

    companion object {
      fun fromRawValue(rawValue: Int): Status? {
        return values().firstOrNull { status -> status.rawValue == rawValue }
      }
    }
  }
}