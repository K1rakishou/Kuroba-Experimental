package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class ChanCatalogSnapshotCache : GenericCacheSource<ChanDescriptor.ICatalogDescriptor, ChanCatalogSnapshot>(
  capacity = 4,
  maxSize = 6,
  cacheEntriesToRemovePerTrim = 3
)