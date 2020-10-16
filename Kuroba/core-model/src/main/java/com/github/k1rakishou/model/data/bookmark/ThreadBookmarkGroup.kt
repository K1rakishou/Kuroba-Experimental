package com.github.k1rakishou.model.data.bookmark

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.ConcurrentHashMap

data class ThreadBookmarkGroup(
  val groupId: String,
  val groupName: String,
  @get:Synchronized
  @set:Synchronized
  var isExpanded: Boolean,
  @get:Synchronized
  @set:Synchronized
  var order: Int,
  // Map<ThreadBookmarkGroupEntryDatabaseId, ThreadBookmarkGroupEntry>
  val entries: ConcurrentHashMap<Long, ThreadBookmarkGroupEntry>
)

data class ThreadBookmarkGroupEntry(
  val databaseId: Long,
  val ownerGroupId: String,
  val ownerBookmarkId: Long,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  @get:Synchronized
  @set:Synchronized
  var orderInGroup: Int,
)

class ThreadBookmarkGroupToCreate(
  val groupId: String,
  val groupName: String,
  val isExpanded: Boolean,
  val order: Int,
  val entries: MutableList<ThreadBookmarkGroupEntryToCreate> = mutableListOf()
)

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
)