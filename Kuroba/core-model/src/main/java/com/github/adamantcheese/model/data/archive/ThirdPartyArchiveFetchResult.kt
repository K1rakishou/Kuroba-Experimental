package com.github.adamantcheese.model.data.archive

import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import org.joda.time.DateTime

data class ThirdPartyArchiveFetchResult(
  val databaseId: Long,
  val archiveDescriptor: ArchiveDescriptor,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val success: Boolean,
  val errorText: String?,
  val insertedOn: DateTime
) {

  init {
    if (success) {
      require(errorText == null) { "success with error text!" }
    } else {
      require(errorText != null) { "error without error text!" }
    }
  }

  companion object {
    fun success(
      archiveDescriptor: ArchiveDescriptor,
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    ): ThirdPartyArchiveFetchResult {
      return ThirdPartyArchiveFetchResult(
        databaseId = 0L,
        archiveDescriptor = archiveDescriptor,
        threadDescriptor = threadDescriptor,
        success = true,
        errorText = null,
        insertedOn = DateTime.now()
      )
    }

    fun error(
      archiveDescriptor: ArchiveDescriptor,
      threadDescriptor: ChanDescriptor.ThreadDescriptor,
      errorText: String
    ): ThirdPartyArchiveFetchResult {
      return ThirdPartyArchiveFetchResult(
        databaseId = 0L,
        archiveDescriptor = archiveDescriptor,
        threadDescriptor = threadDescriptor,
        success = false,
        errorText = errorText,
        insertedOn = DateTime.now()
      )
    }
  }
}