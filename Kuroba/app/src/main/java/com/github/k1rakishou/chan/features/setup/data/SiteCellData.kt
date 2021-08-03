package com.github.k1rakishou.chan.features.setup.data

import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

data class SiteCellData(
  val siteDescriptor: SiteDescriptor,
  val siteIcon: String,
  val siteName: String,
  val siteEnableState: SiteEnableState
)

data class SiteCellArchiveGroupInfo(
  val archives: List<SiteCellData>,
  val isGroupExpanded: Boolean = false,
  val archiveEnabledTotalCount: ArchiveEnabledTotalCount
)

data class ArchiveEnabledTotalCount(val enabledCount: Int, val totalCount: Int)