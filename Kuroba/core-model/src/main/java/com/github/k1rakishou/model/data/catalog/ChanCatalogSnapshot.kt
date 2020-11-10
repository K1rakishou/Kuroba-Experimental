package com.github.k1rakishou.model.data.catalog

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class ChanCatalogSnapshot(
  val boardDescriptor: BoardDescriptor,
  val chanCatalogSnapshotEntryList: List<ChanCatalogSnapshotEntry>
) {

  init {
    var prevOrder = Int.MIN_VALUE

    chanCatalogSnapshotEntryList.forEach { chanCatalogSnapshotEntry ->
      val snapshotEntryBoardDescriptor = chanCatalogSnapshotEntry.threadDescriptor.boardDescriptor

      check(snapshotEntryBoardDescriptor == boardDescriptor) {
        "snapshotEntryBoardDescriptor (${snapshotEntryBoardDescriptor}) != this.boardDescriptor (${boardDescriptor})"
      }

      check(chanCatalogSnapshotEntry.order > prevOrder) {
        "Bad order chanCatalogSnapshotEntry.order=${chanCatalogSnapshotEntry.order}, prevOrder=$prevOrder"
      }

      prevOrder = chanCatalogSnapshotEntry.order
    }
  }

  val catalogThreadDescriptors by lazy {
    return@lazy chanCatalogSnapshotEntryList
      .map { chanCatalogSnapshotEntry -> chanCatalogSnapshotEntry.threadDescriptor }
  }

  fun isEmpty(): Boolean = chanCatalogSnapshotEntryList.isEmpty()

  override fun toString(): String {
    return "ChanCatalogSnapshot{boardDescriptor=$boardDescriptor, " +
      "chanCatalogSnapshotEntryList=${chanCatalogSnapshotEntryList.size}}"
  }

  companion object {
    fun fromSortedThreadDescriptorList(
      boardDescriptor: BoardDescriptor,
      threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
    ): ChanCatalogSnapshot {
      val chanCatalogSnapshotEntryList = threadDescriptors.mapIndexed { order, threadDescriptor ->
        return@mapIndexed ChanCatalogSnapshotEntry(threadDescriptor, order)
      }

      return ChanCatalogSnapshot(
        boardDescriptor,
        chanCatalogSnapshotEntryList
      )
    }
  }

}