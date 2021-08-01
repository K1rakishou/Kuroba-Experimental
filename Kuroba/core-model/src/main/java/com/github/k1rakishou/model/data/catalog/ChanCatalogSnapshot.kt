package com.github.k1rakishou.model.data.catalog

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class ChanCatalogSnapshot(
  val boardDescriptor: BoardDescriptor,
  val isUnlimitedCatalog: Boolean
) {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private var currentCatalogPage: Int? = null
  @GuardedBy("lock")
  private var endReached: Boolean = false

  @GuardedBy("lock")
  private val duplicateChecker = hashSetWithCap<ChanDescriptor.ThreadDescriptor>(32)
  @GuardedBy("lock")
  private val chanCatalogSnapshotEntryList = mutableListWithCap<ChanDescriptor.ThreadDescriptor>(32)

  val chanCatalogSnapshotEntryListCopy: List<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { chanCatalogSnapshotEntryList.toList() }

  val catalogThreadDescriptorList: List<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { chanCatalogSnapshotEntryList }

  val catalogThreadDescriptorSet: Set<ChanDescriptor.ThreadDescriptor>
    get() = lock.read { duplicateChecker }

  val catalogPage: Int?
    get() = lock.read { currentCatalogPage }

  val isEndReached: Boolean
    get() = lock.read { endReached }

  fun isEmpty(): Boolean = lock.read { chanCatalogSnapshotEntryList.isEmpty() }

  fun mergeWith(chanCatalogSnapshot: ChanCatalogSnapshot) {
    add(chanCatalogSnapshot.chanCatalogSnapshotEntryListCopy)
  }

  fun add(catalogSnapshotEntries: List<ChanDescriptor.ThreadDescriptor>) {
    lock.write {
      if (!isUnlimitedCatalog) {
        currentCatalogPage = null
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

  fun getCurrentCatalogPage(): Int? {
    return lock.read {
      if (!isUnlimitedCatalog) {
        return@read null
      }

      return@read currentCatalogPage ?: 1
    }
  }

  fun getNextCatalogPage(): Int? {
    return lock.read {
      if (!isUnlimitedCatalog) {
        return@read null
      }

      return@read currentCatalogPage?.plus(1) ?: 1
    }
  }

  fun onCatalogLoaded(catalogPageToLoad: Int?) {
    lock.write {
      if (isUnlimitedCatalog) {
        currentCatalogPage = catalogPageToLoad ?: 1
      } else {
        currentCatalogPage = null
      }
    }
  }

  fun onEndOfUnlimitedCatalogReached() {
    lock.write {
      if (isUnlimitedCatalog) {
        endReached = true
      }
    }
  }

  override fun toString(): String {
    return "ChanCatalogSnapshot{boardDescriptor=$boardDescriptor, " +
      "chanCatalogSnapshotEntryList=${chanCatalogSnapshotEntryList.size}, " +
      "currentCatalogPage=${currentCatalogPage}}"
  }

  companion object {
    fun fromSortedThreadDescriptorList(
      boardDescriptor: BoardDescriptor,
      threadDescriptors: List<ChanDescriptor.ThreadDescriptor>,
      isUnlimitedCatalog: Boolean
    ): ChanCatalogSnapshot {
      return ChanCatalogSnapshot(boardDescriptor, isUnlimitedCatalog)
        .apply { add(threadDescriptors) }
    }
  }

}