package com.github.adamantcheese.model.data.archive

import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import org.joda.time.DateTime

data class ThirdPartyArchiveFetchResult(
        val archiveDescriptor: ArchiveDescriptor,
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
        fun success(archiveDescriptor: ArchiveDescriptor): ThirdPartyArchiveFetchResult {
            return ThirdPartyArchiveFetchResult(archiveDescriptor, true, null, DateTime.now())
        }

        fun error(archiveDescriptor: ArchiveDescriptor, errorText: String): ThirdPartyArchiveFetchResult {
            return ThirdPartyArchiveFetchResult(archiveDescriptor, false, errorText, DateTime.now())
        }
    }
}