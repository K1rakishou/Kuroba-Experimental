package com.github.adamantcheese.chan.features.archives.data

import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor

data class ArchiveInfo(
  val archiveDescriptor: ArchiveDescriptor,
  val archiveNameWithDomain: String,
  val status: ArchiveStatus,
  val state: ArchiveState,
  val supportedBoards: String,
  val supportedBoardsMedia: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArchiveInfo) return false

        if (archiveDescriptor != other.archiveDescriptor) return false

        return true
    }

    override fun hashCode(): Int {
        return archiveDescriptor.hashCode()
    }
}