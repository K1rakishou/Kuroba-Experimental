package com.github.k1rakishou.model.data.catalog

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class ChanCatalogSnapshotEntry(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val order: Int
)