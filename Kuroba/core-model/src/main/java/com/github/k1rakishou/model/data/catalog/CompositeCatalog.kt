package com.github.k1rakishou.model.data.catalog

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class CompositeCatalog(
  val name: String,
  val compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor
)