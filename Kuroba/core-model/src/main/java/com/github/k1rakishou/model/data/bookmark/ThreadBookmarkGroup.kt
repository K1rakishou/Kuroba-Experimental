package com.github.k1rakishou.model.data.bookmark

import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

class ThreadBookmarkGroup(
  val groupId: String,
  val groupName: String,
  @get:Synchronized
  @set:Synchronized
  var isExpanded: Boolean,
  @get:Synchronized
  @set:Synchronized
  var groupOrder: Int,
  newEntries: Map<Long, ThreadBookmarkGroupEntry> = emptyMap(),
  newOrders: List<Long> = emptyList(),
  newMatchingPattern: ThreadBookmarkGroupMatchPattern?
) {
  @get:Synchronized
  @set:Synchronized
  private var _matchingPattern: ThreadBookmarkGroupMatchPattern? = null
  val matchingPattern: ThreadBookmarkGroupMatchPattern?
    get() = _matchingPattern

  // Map<ThreadBookmarkGroupEntryDatabaseId, ThreadBookmarkGroupEntry>
  private val entries: MutableMap<Long, ThreadBookmarkGroupEntry> = mutableMapWithCap(16)
  // List<ThreadBookmarkGroupEntryDatabaseId>
  private val orders: MutableList<Long> = mutableListWithCap(16)
  private val fastLookupDescriptorSet: MutableSet<ChanDescriptor.ThreadDescriptor> = hashSetWithCap(16)

  init {
    entries.clear()
    entries.putAll(newEntries)

    orders.clear()
    orders.addAll(newOrders)

    fastLookupDescriptorSet.clear()

    entries.values.forEach { threadBookmarkGroupEntry ->
      fastLookupDescriptorSet.add(threadBookmarkGroupEntry.threadDescriptor)
    }

    _matchingPattern = newMatchingPattern
  }

  fun isDefaultGroup(): Boolean = Companion.isDefaultGroup(groupId)

  fun matches(boardDescriptor: BoardDescriptor, postSubject: CharSequence, postComment: CharSequence): Boolean {
    if (isDefaultGroup()) {
      // Default group matches everything
      return true
    }

    return _matchingPattern?.matches(boardDescriptor, postSubject, postComment) ?: false
  }

  @Synchronized
  fun updateMatchingPattern(threadBookmarkGroupMatchPattern: ThreadBookmarkGroupMatchPattern?) {
    _matchingPattern = threadBookmarkGroupMatchPattern
  }

  @Synchronized
  fun removeThreadBookmarkGroupEntry(threadBookmarkGroupEntry: ThreadBookmarkGroupEntry) {
    entries.remove(threadBookmarkGroupEntry.databaseId)
    fastLookupDescriptorSet.remove(threadBookmarkGroupEntry.threadDescriptor)
    orders.remove(threadBookmarkGroupEntry.databaseId)

    checkConsistency()
  }

  @Synchronized
  fun addThreadBookmarkGroupEntry(threadBookmarkGroupEntry: ThreadBookmarkGroupEntry) {
    entries[threadBookmarkGroupEntry.databaseId] = threadBookmarkGroupEntry
    fastLookupDescriptorSet.add(threadBookmarkGroupEntry.threadDescriptor)

    val existingIndex = orders.indexOf(threadBookmarkGroupEntry.databaseId)
    if (existingIndex < 0) {
      orders.add(threadBookmarkGroupEntry.databaseId)
    }
  }

  @Synchronized
  fun addThreadBookmarkGroupEntry(
    reserveDBId: Long,
    threadBookmarkGroupEntry: ThreadBookmarkGroupEntry,
    orderInGroup: Int
  ) {
    entries[threadBookmarkGroupEntry.databaseId] = threadBookmarkGroupEntry
    fastLookupDescriptorSet.add(threadBookmarkGroupEntry.threadDescriptor)

    if (orders[orderInGroup] != reserveDBId) {
      error("Inconsistency detected! expected=${reserveDBId}, actual=${orders[orderInGroup]}")
    }

    orders[orderInGroup] = threadBookmarkGroupEntry.databaseId
  }

  @Synchronized
  fun contains(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return fastLookupDescriptorSet.contains(threadDescriptor)
  }

  @Synchronized
  fun getGroupEntryByThreadDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ThreadBookmarkGroupEntry? {
    return entries.values
      .firstOrNull { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.threadDescriptor == threadDescriptor }
  }

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
  fun getEntriesCount(): Int = entries.size

  @Synchronized
  fun reserveSpaceForBookmarkOrder(reserveDBId: Long): Int {
    orders.add(reserveDBId)
    return orders.lastIndex
  }

  @Synchronized
  fun removeTemporaryOrders(reserveDBId: Long) {
    require(reserveDBId < 0) { "Unexpected: reserveDBId: $reserveDBId" }

    orders.removeAll { order -> order == reserveDBId }
  }

  @Synchronized
  fun checkConsistency() {
    check(entries.size == orders.size) {
      "Inconsistency detected! entries.size=${entries.size}, orders.size=${orders.size}"
    }
    check(entries.size == fastLookupDescriptorSet.size) {
      "Inconsistency detected! entries.size=${entries.size}, fastLookupDescriptorSet.size=${fastLookupDescriptorSet.size}"
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadBookmarkGroup

    if (groupId != other.groupId) return false
    if (groupName != other.groupName) return false
    if (isExpanded != other.isExpanded) return false
    if (groupOrder != other.groupOrder) return false
    if (entries != other.entries) return false
    if (orders != other.orders) return false
    if (fastLookupDescriptorSet != other.fastLookupDescriptorSet) return false

    return true
  }

  override fun hashCode(): Int {
    var result = groupId.hashCode()
    result = 31 * result + groupName.hashCode()
    result = 31 * result + isExpanded.hashCode()
    result = 31 * result + groupOrder
    result = 31 * result + entries.hashCode()
    result = 31 * result + orders.hashCode()
    result = 31 * result + fastLookupDescriptorSet.hashCode()
    return result
  }

  override fun toString(): String {
    return "ThreadBookmarkGroup(groupId='$groupId', groupName='$groupName', " +
      "isExpanded=$isExpanded, groupOrder=$groupOrder, entriesCount=${entries.size}, " +
      "ordersCount=${orders.size}, fastLookupDescriptorSetCount=${fastLookupDescriptorSet.size})"
  }

  companion object {
    private val RESERVE_DB_ID = AtomicLong(-1L)

    const val DEFAULT_GROUP_ID = "default_group"
    const val DEFAULT_GROUP_NAME = "Default group"

    const val MAX_MATCH_GROUPS = 8

    private val WHITESPACE_PATTERN = Pattern.compile("\\s+").toRegex()

    fun isDefaultGroup(groupId: String): Boolean {
      return groupId == DEFAULT_GROUP_ID
    }

    fun rawGroupNameToGroupId(groupName: String): String? {
      val groupId = groupName.trim().replace(WHITESPACE_PATTERN, "_")
      if (groupId.isBlank()) {
        return null
      }

      return groupId
    }

    fun nextReserveDBId(): Long {
      return RESERVE_DB_ID.getAndAdd(-1)
    }
  }
}

data class ThreadBookmarkGroupEntry(
  val databaseId: Long,
  val ownerGroupId: String,
  val ownerBookmarkId: Long,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor
)

data class SimpleThreadBookmarkGroupToCreate(
  val groupName: String,
  val entries: List<ChanDescriptor.ThreadDescriptor> = mutableListOf(),
  val matchingPattern: ThreadBookmarkGroupMatchPattern?
)

class ThreadBookmarkGroupToCreate(
  val reserveDBId: Long,
  val groupId: String,
  val groupName: String,
  val isExpanded: Boolean,
  val groupOrder: Int,
  val entries: MutableList<ThreadBookmarkGroupEntryToCreate> = mutableListOf(),
  val matchingPattern: ThreadBookmarkGroupMatchPattern?
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

data class ThreadBookmarkGroupEntryToCreate(
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