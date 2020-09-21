package com.github.k1rakishou.chan.features.setup.data

import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class SiteCellData(
  val siteDescriptor: SiteDescriptor,
  val siteIcon: String,
  val siteName: String,
  val siteEnableState: SiteEnableState
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SiteCellData

    if (siteDescriptor != other.siteDescriptor) return false

    return true
  }

  override fun hashCode(): Int {
    return siteDescriptor.hashCode()
  }

}