package com.github.k1rakishou.model.data.bookmark

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class ThreadBookmarkGroup(
  val groupId: String,
  val groupName: String,
  @get:Synchronized
  @set:Synchronized
  var isExpanded: Boolean,
  @get:Synchronized
  @set:Synchronized
  var groupOrder: Int,
  // Map<ThreadBookmarkGroupEntryDatabaseId, ThreadBookmarkGroupEntry>
  private val entries: MutableMap<Long, ThreadBookmarkGroupEntry>,
  // List<ThreadBookmarkGroupEntryDatabaseId>
  private val orders: MutableList<Long>
) {

  @Synchronized
  fun getBookmarkDescriptors(): List<ChanDescriptor.ThreadDescriptor> {
    return entries.values
      .map { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.threadDescriptor }
  }

  @Synchronized
  fun iterateEntriesOrderedWhile(iterator: (Int, ThreadBookmarkGroupEntry) -> Boolean) {
    checkConsistency()

    orders.forEachIndexed { index, threadBookmarkGroupEntryDatabaseId ->
      val threadBookmarkGroupEntry = requireNotNull(entries[threadBookmarkGroupEntryDatabaseId]) {
        "No threadBookmarkGroupEntry found for " +
          "threadBookmarkGroupEntryDatabaseId=$threadBookmarkGroupEntryDatabaseId"
      }

      if (!iterator(index, threadBookmarkGroupEntry)) {
        return
      }
    }
  }

  @Synchronized
  fun removeThreadBookmarkGroupEntry(threadBookmarkGroupEntry: ThreadBookmarkGroupEntry) {
    entries.remove(threadBookmarkGroupEntry.databaseId)
    removeDatabaseId(threadBookmarkGroupEntry.databaseId)

    checkConsistency()
  }

  @Synchronized
  fun addThreadBookmarkGroupEntry(threadBookmarkGroupEntry: ThreadBookmarkGroupEntry) {
    entries[threadBookmarkGroupEntry.databaseId] = threadBookmarkGroupEntry
    addDatabaseId(threadBookmarkGroupEntry.databaseId)
  }

  @Synchronized
  fun addThreadBookmarkGroupEntry(threadBookmarkGroupEntry: ThreadBookmarkGroupEntry, orderInGroup: Int) {
    entries[threadBookmarkGroupEntry.databaseId] = threadBookmarkGroupEntry

    check(orders[orderInGroup] == RESERVE_DB_ID) {
      "Inconsistency detected! orders[orderInGroup]=${orders[orderInGroup]}"
    }

    orders[orderInGroup] = threadBookmarkGroupEntry.databaseId
  }

  @Synchronized
  fun getEntriesCount(): Int = entries.size

  @Synchronized
  fun containsEntryWithThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return entries
      .values
      .any { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.threadDescriptor == threadDescriptor }
  }

  @Synchronized
  private fun removeDatabaseId(databaseId: Long) {
    orders.remove(databaseId)
  }

  @Synchronized
  private fun addDatabaseId(newDatabaseId: Long) {
    val index = orders.indexOf(newDatabaseId)
    if (index >= 0) {
      return
    }

    orders.add(newDatabaseId)
  }

  @Synchronized
  fun reserveSpaceForBookmarkOrder(): Int {
    orders.add(RESERVE_DB_ID)
    return orders.lastIndex
  }

  @Synchronized
  fun removeTemporaryOrders() {
    orders.removeAll { order -> order == RESERVE_DB_ID }
  }

  @Synchronized
  fun checkConsistency() {
    check(entries.size == orders.size) {
      "Inconsistency detected! entries.size=${entries.size}, orders.size=${orders.size}"
    }
  }

  @Synchronized
  fun moveBookmark(
    fromBookmarkDescriptor: ChanDescriptor.ThreadDescriptor,
    toBookmarkDescriptor: ChanDescriptor.ThreadDescriptor
  ): Boolean {
    val fromDatabaseId = entries.values
      .firstOrNull { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.threadDescriptor == fromBookmarkDescriptor }
      ?.databaseId
      ?: return false

    val toDatabaseId = entries.values
      .firstOrNull { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.threadDescriptor == toBookmarkDescriptor }
      ?.databaseId
      ?: return false

    val fromIndex = orders.indexOfFirst { databaseId -> databaseId == fromDatabaseId }
    if (fromIndex < 0) {
      return false
    }

    val toIndex = orders.indexOfFirst { databaseId -> databaseId == toDatabaseId }
    if (toIndex < 0) {
      return false
    }

    orders.add(toIndex, orders.removeAt(fromIndex))
    return true
  }

  companion object {
    const val RESERVE_DB_ID = -1L
  }
}

class ThreadBookmarkGroupEntry(
  val databaseId: Long,
  val ownerGroupId: String,
  val ownerBookmarkId: Long,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor
)

class SimpleThreadBookmarkGroupToCreate(
  val groupName: String,
  val entries: List<ChanDescriptor.ThreadDescriptor> = mutableListOf()
)

class ThreadBookmarkGroupToCreate(
  val groupId: String,
  val groupName: String,
  val isExpanded: Boolean,
  val groupOrder: Int,
  val entries: MutableList<ThreadBookmarkGroupEntryToCreate> = mutableListOf()
) {

  fun getEntryDatabaseIdsSorted(): List<Long> {
    return entries
      .sortedBy { threadBookmarkGroupEntryToCreate -> threadBookmarkGroupEntryToCreate.orderInGroup }
      .map { threadBookmarkGroupEntryToCreate -> threadBookmarkGroupEntryToCreate.databaseId }
  }

  override fun toString(): String {
    return "ThreadBookmarkGroupToCreate(groupId='$groupId', groupName='$groupName', " +
      "isExpanded=$isExpanded, groupOrder=$groupOrder, entriesCount=${entries.size})"
  }
}

class ThreadBookmarkGroupEntryToCreate(
  @get:Synchronized
  @set:Synchronized
  var databaseId: Long = -1L,
  @get:Synchronized
  @set:Synchronized
  var ownerBookmarkId: Long = -1L,
  val ownerGroupId: String,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val orderInGroup: Int
) {

  override fun toString(): String {
    return "ThreadBookmarkGroupEntryToCreate(databaseId=$databaseId, ownerBookmarkId=$ownerBookmarkId, " +
      "ownerGroupId='$ownerGroupId', threadDescriptor=$threadDescriptor, orderInGroup=$orderInGroup)"
  }
}