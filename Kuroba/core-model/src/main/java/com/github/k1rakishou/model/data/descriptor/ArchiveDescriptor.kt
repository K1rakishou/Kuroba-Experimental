package com.github.k1rakishou.model.data.descriptor

import com.github.k1rakishou.model.data.archive.ArchiveType

class ArchiveDescriptor(
  val archiveId: Long,
  val name: String,
  val domain: String,
  val archiveType: ArchiveType
) {
  val siteDescriptor by lazy { SiteDescriptor(domain) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ArchiveDescriptor) return false

    if (domain != other.domain) return false

    return true
  }

  override fun hashCode(): Int {
    return domain.hashCode()
  }

  override fun toString(): String {
    return "AD(name='$name', domain='$domain')"
  }

  companion object {
    // Default value for archiveId key in the database. If a post has archiveId == 0L that means the
    // post was fetched from the original server and not from an archive.
    const val NO_ARCHIVE_ID = 0L

    @JvmStatic
    fun isActualArchive(archiveId: Long): Boolean {
      return archiveId != NO_ARCHIVE_ID
    }
  }
}