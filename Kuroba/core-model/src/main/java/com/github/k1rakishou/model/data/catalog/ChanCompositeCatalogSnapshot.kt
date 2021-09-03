package com.github.k1rakishou.model.data.catalog

import androidx.annotation.GuardedBy
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ChanCompositeCatalogSnapshot(
  override val catalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor,
  private val chanCatalogSnapshots: LinkedHashMap<ChanDescriptor.CatalogDescriptor, ChanCatalogSnapshot> = LinkedHashMap(),
  override val isUnlimitedCatalog: Boolean = false
) : IChanCatalogSnapshot<ChanDescriptor.CompositeCatalogDescriptor> {

  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private var currentCatalogPage: Int
  @GuardedBy("lock")
  private var endReached: Boolean = false

  init {
    currentCatalogPage = getStartCatalogPage()
  }

  override val isUnlimitedOrCompositeCatalog: Boolean
    get() = true
  override val catalogThreadDescriptorList: List<ChanDescriptor.ThreadDescriptor>
    get() = lock.read {
      chanCatalogSnapshots.values.flatMap { chanCatalogSnapshot ->
        chanCatalogSnapshot.catalogThreadDescriptorList
      }
    }
  override val catalogThreadDescriptorSet: Set<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { catalogThreadDescriptorList.toSet() }
  override val catalogPage: Int
    get() = lock.read { currentCatalogPage }
  override val isEndReached: Boolean
    get() = lock.read { endReached }
  override val postsCount: Int
    get() = lock.read {
      chanCatalogSnapshots.values.sumOf { chanCatalogSnapshot -> chanCatalogSnapshot.postsCount }
    }

  fun get(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ChanCatalogSnapshot? {
    return lock.read { chanCatalogSnapshots[catalogDescriptor] }
  }

  override fun isEmpty(): Boolean {
    return lock.read { chanCatalogSnapshots.values.all { chanCatalogSnapshot -> chanCatalogSnapshot.isEmpty() } }
  }

  override fun mergeWith(chanCatalogSnapshot: IChanCatalogSnapshot<ChanDescriptor.CompositeCatalogDescriptor>) {
    lock.write {
      if (chanCatalogSnapshot is ChanCompositeCatalogSnapshot) {
        chanCatalogSnapshot.chanCatalogSnapshots.entries.forEach { (catalogDescriptor, chanCatalogSnapshot) ->
          if (chanCatalogSnapshots.containsKey(catalogDescriptor)) {
            chanCatalogSnapshots[catalogDescriptor]!!.mergeWith(chanCatalogSnapshot)
          } else {
            chanCatalogSnapshots[catalogDescriptor] = chanCatalogSnapshot
          }
        }
      } else {
        chanCatalogSnapshot as ChanCatalogSnapshot

        val snapshot = chanCatalogSnapshots.getOrPut(
          key = chanCatalogSnapshot.catalogDescriptor,
          defaultValue = {
            return@getOrPut ChanCatalogSnapshot(
              catalogDescriptor = chanCatalogSnapshot.catalogDescriptor,
              isUnlimitedCatalog = chanCatalogSnapshot.isUnlimitedCatalog
            )
          }
        )

        snapshot.mergeWith(chanCatalogSnapshot)
      }
    }
  }

  override fun add(catalogSnapshotEntries: List<ChanDescriptor.ThreadDescriptor>) {
    lock.write {
      catalogSnapshotEntries
        .groupBy { catalogSnapshotEntry -> catalogSnapshotEntry.catalogDescriptor() }
        .forEach { (catalogDescriptor, threadDescriptors) ->
          val snapshot = chanCatalogSnapshots.getOrPut(
            key = catalogDescriptor,
            defaultValue = {
              return@getOrPut ChanCatalogSnapshot(
                catalogDescriptor = catalogDescriptor,
                isUnlimitedCatalog = isUnlimitedCatalog
              )
            }
          )

          snapshot.add(threadDescriptors)
        }
    }
  }

  override fun getNextCatalogPage(): Int {
    return lock.write {
      if (endReached) {
        error("End had already been reached, can't load next page")
      }

      return@write currentCatalogPage.plus(1)
    }
  }

  override fun onCatalogLoaded(catalogPageToLoad: Int?) {
    lock.write {
      currentCatalogPage = catalogPageToLoad ?: getStartCatalogPage()
    }
  }

  override fun onEndOfUnlimitedCatalogReached() {
    lock.write {
      endReached = true
    }
  }

  override fun updateCatalogPage(overridePage: Int) {
    lock.write {
      if (isUnlimitedOrCompositeCatalog) {
        endReached = false
        currentCatalogPage = (overridePage - 1).coerceAtLeast(0)
      }
    }
  }

  private fun getStartCatalogPage(): Int {
    return START_PAGE_COMPOSITE_CATALOG
  }

  override fun toString(): String {
    return "ChanCompositeCatalogSnapshot{catalogDescriptor=$catalogDescriptor, " +
      "catalogThreadDescriptorList=${catalogThreadDescriptorList.size}, " +
      "currentCatalogPage=${currentCatalogPage}, endReached=${endReached}}"
  }

  companion object {
    const val START_PAGE_COMPOSITE_CATALOG = 0
  }

}