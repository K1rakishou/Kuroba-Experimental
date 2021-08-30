package com.github.k1rakishou.model.data.catalog

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class ChanCatalogSnapshot(
  val catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
  val isUnlimitedCatalog: Boolean
) {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private var currentCatalogPage: Int
  @GuardedBy("lock")
  private var endReached: Boolean = false

  @GuardedBy("lock")
  private val duplicateChecker = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(32)
  @GuardedBy("lock")
  private val chanCatalogSnapshotEntryList = mutableListWithCap<ChanDescriptor.ThreadDescriptor>(32)

  val isUnlimitedOrCompositeCatalog: Boolean
    get() = isUnlimitedCatalog || catalogDescriptor is ChanDescriptor.CompositeCatalogDescriptor

  val catalogThreadDescriptorList: List<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { chanCatalogSnapshotEntryList.toList() }

  val catalogThreadDescriptorSet: Set<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { duplicateChecker.toSet() }

  val catalogPage: Int
    get() = lock.read { currentCatalogPage }

  val isEndReached: Boolean
    get() = lock.read { endReached }

  val postsCount: Int
    get() = lock.read { chanCatalogSnapshotEntryList.size }

  init {
    currentCatalogPage = getStartCatalogPage()
  }

  fun isEmpty(): Boolean = lock.read { chanCatalogSnapshotEntryList.isEmpty() }

  fun mergeWith(chanCatalogSnapshot: ChanCatalogSnapshot) {
    add(chanCatalogSnapshot.catalogThreadDescriptorList)
  }

  fun add(catalogSnapshotEntries: List<ChanDescriptor.ThreadDescriptor>) {
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

  fun getNextCatalogPage(): Int? {
    return lock.read {
      if (!isUnlimitedOrCompositeCatalog) {
        return@read null
      }

      return@read when (catalogDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> currentCatalogPage.plus(1)
        is ChanDescriptor.CompositeCatalogDescriptor -> currentCatalogPage.plus(1)
      }
    }
  }

  fun onCatalogLoaded(catalogPageToLoad: Int?) {
    lock.write {
      if (isUnlimitedOrCompositeCatalog) {
        currentCatalogPage = catalogPageToLoad ?: getStartCatalogPage()
      } else {
        currentCatalogPage = getStartCatalogPage()
      }
    }
  }

  fun onEndOfUnlimitedCatalogReached() {
    lock.write {
      if (isUnlimitedOrCompositeCatalog) {
        endReached = true
      }
    }
  }

  fun updateCatalogPage(overridePage: Int) {
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
    return when (catalogDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> START_PAGE_UNLIMITED_CATALOG
      is ChanDescriptor.CompositeCatalogDescriptor -> START_PAGE_COMPOSITE_CATALOG
    }
  }

  override fun toString(): String {
    return "ChanCatalogSnapshot{catalogDescriptor=$catalogDescriptor, " +
      "chanCatalogSnapshotEntryList=${chanCatalogSnapshotEntryList.size}, " +
      "currentCatalogPage=${currentCatalogPage}, endReached=${endReached}}"
  }

  companion object {
    private const val START_PAGE_COMPOSITE_CATALOG = 0
    private const val START_PAGE_UNLIMITED_CATALOG = 1

    fun fromSortedThreadDescriptorList(
      catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
      threadDescriptors: List<ChanDescriptor.ThreadDescriptor>,
      isUnlimitedCatalog: Boolean
    ): ChanCatalogSnapshot {
      return ChanCatalogSnapshot(catalogDescriptor, isUnlimitedCatalog)
        .apply { add(threadDescriptors) }
    }
  }

}