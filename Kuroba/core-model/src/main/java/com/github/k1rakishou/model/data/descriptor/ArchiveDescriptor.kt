package com.github.k1rakishou.model.data.descriptor

import com.github.k1rakishou.common.data.ArchiveType

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

}