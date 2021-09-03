package com.github.k1rakishou.model.data.catalog

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class ChanCatalogSnapshot(
  override val catalogDescriptor: ChanDescriptor.CatalogDescriptor,
  override val isUnlimitedCatalog: Boolean
) : IChanCatalogSnapshot<ChanDescriptor.CatalogDescriptor> {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private var currentCatalogPage: Int
  @GuardedBy("lock")
  private var endReached: Boolean = false

  @GuardedBy("lock")
  private val duplicateChecker = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(32)
  @GuardedBy("lock")
  private val chanCatalogSnapshotEntryList = mutableListWithCap<ChanDescriptor.ThreadDescriptor>(32)

  override val isUnlimitedOrCompositeCatalog: Boolean
    get() = isUnlimitedCatalog
  override val catalogThreadDescriptorList: List<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { chanCatalogSnapshotEntryList.toList() }
  override val catalogThreadDescriptorSet: Set<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { duplicateChecker.toSet() }
  override val catalogPage: Int
    get() = lock.read { currentCatalogPage }
  override val isEndReached: Boolean
    get() = lock.read { endReached }
  override val postsCount: Int
    get() = lock.read { chanCatalogSnapshotEntryList.size }

  init {
    currentCatalogPage = getStartCatalogPage()
  }

  override fun isEmpty(): Boolean = lock.read { chanCatalogSnapshotEntryList.isEmpty() }

  override fun mergeWith(chanCatalogSnapshot: IChanCatalogSnapshot<ChanDescriptor.CatalogDescriptor>) {
    add(chanCatalogSnapshot.catalogThreadDescriptorList)
  }

  override fun add(catalogSnapshotEntries: List<ChanDescriptor.ThreadDescriptor>) {
    lock.write {
      if (!isUnlimitedOrCompositeCatalog) {
        currentCatalogPage = getStartCatalogPage()
        duplicateChecker.clear()
        chanCatalogSnapshotEntryList.clear()
      }

      catalogSnapshotEntries.forEach { catalogSnapshotEntry ->
        if (!duplicateChecker.add(catalogSnapshotEntry)) {
          return@forEach
        }

        chanCatalogSnapshotEntryList.add(catalogSnapshotEntry)
      }
    }
  }

  override fun getNextCatalogPage(): Int? {
    return lock.read {
      if (!isUnlimitedOrCompositeCatalog) {
        return@read null
      }

      if (endReached) {
        error("End had already been reached, can't load next page")
      }

      return@read currentCatalogPage.plus(1)
    }
  }

  override fun onCatalogLoaded(catalogPageToLoad: Int?) {
    lock.write {
      if (isUnlimitedOrCompositeCatalog) {
        currentCatalogPage = catalogPageToLoad ?: getStartCatalogPage()
      } else {
        currentCatalogPage = getStartCatalogPage()
      }
    }
  }

  override fun onEndOfUnlimitedCatalogReached() {
    lock.write {
      if (isUnlimitedOrCompositeCatalog) {
        endReached = true
      }
    }
  }

  override fun updateCatalogPage(overridePage: Int) {
    lock.write {
      if (isUnlimitedOrCompositeCatalog) {
        endReached = false
        currentCatalogPage = (overridePage - 1).coerceAtLeast(0)
        duplicateChecker.clear()
        chanCatalogSnapshotEntryList.clear()
      }
    }
  }

  private fun getStartCatalogPage(): Int {
    return START_PAGE_UNLIMITED_CATALOG
  }

  override fun toString(): String {
    return "ChanCatalogSnapshot{catalogDescriptor=$catalogDescriptor, " +
      "chanCatalogSnapshotEntryList=${chanCatalogSnapshotEntryList.size}, " +
      "currentCatalogPage=${currentCatalogPage}, endReached=${endReached}}"
  }

  companion object {
    const val START_PAGE_UNLIMITED_CATALOG = 1
  }

}