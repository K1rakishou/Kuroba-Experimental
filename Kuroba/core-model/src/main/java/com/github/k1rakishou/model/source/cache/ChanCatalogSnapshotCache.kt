package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.model.data.catalog.ChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

class ChanCatalogSnapshotCache : GenericCacheSource<BoardDescriptor, ChanCatalogSnapshot>(
  capacity = 4,
  maxSize = 6,
  cacheEntriesToRemovePerTrim = 3
)