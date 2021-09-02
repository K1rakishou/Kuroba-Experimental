package com.github.k1rakishou.model.source.cache

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.catalog.ChanCompositeCatalogSnapshot
import com.github.k1rakishou.model.data.catalog.IChanCatalogSnapshot
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlin.concurrent.read
import kotlin.concurrent.write

class ChanCatalogSnapshotCache : GenericCacheSource<
  ChanDescriptor.ICatalogDescriptor,
  IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>
  >(
  capacity = 4,
  maxSize = 6,
  cacheEntriesToRemovePerTrim = 3
) {
  override fun get(key: ChanDescriptor.ICatalogDescriptor): IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>? {
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

            return@read snapshot.get(key) as IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>?
          }
        }
      }

      return@read null
    }
  }

  override fun getOrPut(
    key: ChanDescriptor.ICatalogDescriptor,
    valueFunc: () -> IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>
  ): IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor> {
    return lock.write {
      val prevValue = get(key)
      if (prevValue != null) {
        return@write prevValue
      }

      val newValue = valueFunc()
      actualCache[key] = newValue

      return@write newValue
    }
  }

  override fun delete(key: ChanDescriptor.ICatalogDescriptor) {
    // This method should be separated for CatalogDescriptor/CompositeCatalogDescriptor
    super.delete(key)
  }

  override fun getMany(keys: List<ChanDescriptor.ICatalogDescriptor>): Map<ChanDescriptor.ICatalogDescriptor, IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun getAll(): Map<ChanDescriptor.ICatalogDescriptor, IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun filterValues(filterFunc: (IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>) -> Boolean): List<IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun store(key: ChanDescriptor.ICatalogDescriptor, value: IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>) {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun storeMany(entries: Map<ChanDescriptor.ICatalogDescriptor, IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>>) {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun firstOrNull(predicate: (IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>) -> Boolean): IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>? {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun iterateWhile(iteratorFunc: (IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>) -> Boolean): ModularResult<Unit> {
    error("Not implemented because not used by ChanCatalogSnapshotCache")
  }

  override fun updateMany(keys: List<ChanDescriptor.ICatalogDescriptor>, updateFunc: (IChanCatalogSnapshot<ChanDescriptor.ICatalogDescriptor>) -> Unit) {
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