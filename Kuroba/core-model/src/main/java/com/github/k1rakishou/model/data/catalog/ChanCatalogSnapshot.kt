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
  private var currentCatalogPage: Int = START_PAGE
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

  val postsCount: Int
    get() = lock.read { chanCatalogSnapshotEntryList.size }

  fun isEmpty(): Boolean = lock.read { chanCatalogSnapshotEntryList.isEmpty() }

  fun mergeWith(chanCatalogSnapshot: ChanCatalogSnapshot) {
    add(chanCatalogSnapshot.chanCatalogSnapshotEntryListCopy)
  }

  fun add(catalogSnapshotEntries: List<ChanDescriptor.ThreadDescriptor>) {
    lock.write {
      if (!isUnlimitedCatalog) {
        currentCatalogPage = START_PAGE
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
      if (!isUnlimitedCatalog) {
        return@read null
      }

      return@read currentCatalogPage.plus(1)
    }
  }

  fun onCatalogLoaded(catalogPageToLoad: Int?) {
    lock.write {
      if (isUnlimitedCatalog) {
        currentCatalogPage = catalogPageToLoad ?: START_PAGE
      } else {
        currentCatalogPage = START_PAGE
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

  fun updateCatalogPage(overridePage: Int) {
    lock.write {
      if (isUnlimitedCatalog) {
        endReached = false
        currentCatalogPage = (overridePage - 1).coerceAtLeast(0)
        duplicateChecker.clear()
        chanCatalogSnapshotEntryList.clear()
      }
    }
  }

  override fun toString(): String {
    return "ChanCatalogSnapshot{boardDescriptor=$boardDescriptor, " +
      "chanCatalogSnapshotEntryList=${chanCatalogSnapshotEntryList.size}, " +
      "currentCatalogPage=${currentCatalogPage}, endReached=${endReached}}"
  }

  companion object {
    private const val START_PAGE = 1

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