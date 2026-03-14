package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.catalog.ChanCompositeCatalogSnapshot
import com.github.k1rakishou.model.data.catalog.IChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlin.concurrent.read

typealias CatalogSnapshot = IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>

class ChanCatalogSnapshotCache : GenericCacheSource<
  ChanDescriptor.ICatalogDescriptor,
  CatalogSnapshot
>(
  capacity = 4,
  maxSize = 6,
  cacheEntriesToRemovePerTrim = 3
) {
  override fun get(key: ChanDescriptor.ICatalogDescriptor): CatalogSnapshot? {
    val fromCache = super.get(key)
    if (fromCache != null) {
      return fromCache
    }

    return lock.read {
      if (key is ChanDescriptor.CatalogDescriptor) {
        for (cacheKey in actualCache.keys) {
          if (cacheKey is ChanDescriptor.CompositeCatalogDescriptor && cacheKey.asSet.contains(key)) {
            val snapshot = actualCache[cacheKey] as ChanCompositeCatalogSnapshot?
              ?: return@read null

            return@read snapshot.get(key) as CatalogSnapshot?
          }
        }
      }

      return@read null
    }
  }

  override fun getMany(
    keys: List<ChanDescriptor.ICatalogDescriptor>
  ): Map<ChanDescriptor.ICatalogDescriptor, CatalogSnapshot> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun getAll(
  ): Map<ChanDescriptor.ICatalogDescriptor, CatalogSnapshot> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun filterValues(
    filterFunc: (CatalogSnapshot) -> Boolean
  ): List<CatalogSnapshot> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun store(
    key: ChanDescriptor.ICatalogDescriptor,
    value: CatalogSnapshot
  ) {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun storeMany(
    entries: Map<ChanDescriptor.ICatalogDescriptor, CatalogSnapshot>
  ) {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun firstOrNull(
    predicate: (CatalogSnapshot) -> Boolean
  ): CatalogSnapshot? {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun iterateWhile(
    iteratorFunc: (CatalogSnapshot) -> Boolean
  ): ModularResult<Unit> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun updateMany(
    keys: List<ChanDescriptor.ICatalogDescriptor>,
    updateFunc: (CatalogSnapshot) -> Unit
  ) {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun contains(key: ChanDescriptor.ICatalogDescriptor): Boolean {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun size(): Int {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun deleteMany(keys: List<ChanDescriptor.ICatalogDescriptor>) {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun clear() {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }
}