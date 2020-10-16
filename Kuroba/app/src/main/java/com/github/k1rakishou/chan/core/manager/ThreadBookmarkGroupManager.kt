package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.features.bookmarks.data.GroupOfThreadBookmarkItemViews
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.model.data.bookmark.*
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadBookmarkGroupManager(
  private val appScope: CoroutineScope,
  private val verboseLogs: Boolean,
  private val threadBookmarkGroupEntryRepository: ThreadBookmarkGroupRepository,
  private val bookmarksManager: BookmarksManager
) {
  private val mutex = Mutex()
  private val suspendableInitializer = SuspendableInitializer<Unit>("ThreadBookmarkGroupManager")

  @GuardedBy("mutex")
  // Map<GroupId, ThreadBookmarkGroupEntry>
  private val groupsByGroupIdMap = mutableMapOf<String, ThreadBookmarkGroup>()

  init {
    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .asFlow()
        .flowOn(Dispatchers.Default)
        .collect { bookmarkChange -> handleBookmarkChange(bookmarkChange) }
    }
  }

  fun initialize() {
    appScope.launch(Dispatchers.Default) {
      when (val groupsResult = threadBookmarkGroupEntryRepository.initialize()) {
        is ModularResult.Value -> {
          mutex.withLock {
            groupsByGroupIdMap.clear()

            groupsResult.value.forEach { threadBookmarkGroup ->
              groupsByGroupIdMap[threadBookmarkGroup.groupId] = threadBookmarkGroup
            }
          }

          Logger.d(TAG, "ThreadBookmarkGroupEntryManager initialized! " +
            "Loaded ${groupsByGroupIdMap.size} total bookmark groups")

          suspendableInitializer.initWithValue(Unit)
        }
        is ModularResult.Error -> {
          Logger.e(TAG, "Exception while initializing ThreadBookmarkGroupEntryManager", groupsResult.error)
          suspendableInitializer.initWithError(groupsResult.error)
        }
      }
    }
  }

  suspend fun groupBookmarks(
    threadBookmarkViewList: List<ThreadBookmarkItemView>
  ): List<GroupOfThreadBookmarkItemViews> {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }

    if (threadBookmarkViewList.isEmpty()) {
      return emptyList()
    }

    return mutex.withLock {
      val groups = threadBookmarkViewList
        .map { threadBookmarkItemView -> threadBookmarkItemView.groupId }
        .toSet()
      val threadBookmarkViewMapByDescriptor = threadBookmarkViewList
        .associateBy { threadBookmarkView -> threadBookmarkView.threadDescriptor }

      val sortedGroups = groups
        .mapNotNull { groupId -> groupsByGroupIdMap[groupId] }
        .sortedBy { threadBookmarkGroup -> threadBookmarkGroup.groupOrder }

      val listOfGroups = mutableListWithCap<GroupOfThreadBookmarkItemViews>(sortedGroups.size)

      sortedGroups.forEach { threadBookmarkGroup ->
        val threadBookmarkItemViews =
          mutableListWithCap<ThreadBookmarkItemView>(threadBookmarkGroup.getEntriesCount())

        threadBookmarkGroup.iterateEntriesOrderedWhile { _, threadBookmarkGroupEntry ->
          val orderedThreadBookmarkItemView =
            threadBookmarkViewMapByDescriptor[threadBookmarkGroupEntry.threadDescriptor]

          threadBookmarkItemViews += requireNotNull(orderedThreadBookmarkItemView) {
            "Couldn't find ThreadBookmarkItemView by thread " +
              "descriptor ${threadBookmarkGroupEntry.threadDescriptor}"
          }

          return@iterateEntriesOrderedWhile true
        }

        listOfGroups += GroupOfThreadBookmarkItemViews(
          groupId = threadBookmarkGroup.groupId,
          groupInfoText = threadBookmarkGroup.groupName,
          isExpanded = threadBookmarkGroup.isExpanded,
          threadBookmarkItemViews = threadBookmarkItemViews
        )
      }

      return@withLock listOfGroups
    }
  }

  suspend fun toggleBookmarkExpandState(groupId: String): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }

    return mutex.withLock {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLock false

      val newIsExpanded = !group.isExpanded
      group.isExpanded = newIsExpanded

      threadBookmarkGroupEntryRepository.updateBookmarkGroupExpanded(groupId, newIsExpanded)
        .safeUnwrap { error ->
          groupsByGroupIdMap[groupId]?.isExpanded = !newIsExpanded

          Logger.e(TAG, "updateBookmarkGroupExpanded error", error)
          return@withLock false
        }

      return@withLock true
    }
  }

  suspend fun createGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    // Yes, we are locking the database access here, because we need to calculate the new order for
    // groups and and regular entries and we don't want anyone modifying groupsByGroupIdMap while
    // we are persisting the changes.
    return mutex.withLock {
      val createTransaction = CreateBookmarkGroupEntriesTransaction()

      bookmarksManager.viewBookmarks(bookmarkThreadDescriptors) { threadBookmarkView ->
        val groupId = threadBookmarkView.groupId

        val groupOrder = if (groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId]!!.groupOrder
        } else {
          groupsByGroupIdMap.values
            .maxOfOrNull { threadBookmarkGroup -> threadBookmarkGroup.groupOrder }
            ?.plus(1) ?: 0
        }

        val needCreateGroup = groupsByGroupIdMap[groupId] == null

        if (!groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId] = ThreadBookmarkGroup(
            groupId = groupId,
            groupName = groupId,
            isExpanded = false,
            groupOrder = groupOrder,
            entries = mutableMapOf(),
            orders = mutableListOf()
          )
        }

        if (!createTransaction.toCreate.containsKey(groupId)) {
          createTransaction.toCreate[groupId] = ThreadBookmarkGroupToCreate(
            groupId = groupId,
            groupName = groupId,
            isExpanded = false,
            needCreate = needCreateGroup,
            groupOrder = groupOrder,
            entries = mutableListOf()
          )
        }

        val containsBookmarkEntry = groupsByGroupIdMap[groupId]!!.containsEntryWithThreadDescriptor(
          threadBookmarkView.threadDescriptor
        )

        if (containsBookmarkEntry) {
          return@viewBookmarks
        }

        val newBookmarkOrder = groupsByGroupIdMap[groupId]!!.reserveSpaceForBookmarkOrder()
        val threadBookmarkGroupToCreate = createTransaction.toCreate[groupId]!!

        threadBookmarkGroupToCreate.entries.add(
          ThreadBookmarkGroupEntryToCreate(
            ownerGroupId = groupId,
            threadDescriptor = threadBookmarkView.threadDescriptor,
            orderInGroup = newBookmarkOrder
          )
        )
      }

      if (createTransaction.isEmpty()) {
        return@withLock true
      }

      threadBookmarkGroupEntryRepository.executeCreateTransaction(createTransaction)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error trying to insert new bookmark group entries into the database", error)

          // Remove all databaseIds that == -1L from orders.
          createTransaction.toCreate.keys.forEach { groupId ->
            groupsByGroupIdMap[groupId]?.removeTemporaryOrders()
          }

          return@withLock false
        }

      createTransaction.toCreate.forEach { (groupId, threadBookmarkGroupToCreate) ->
        val threadBookmarkGroupEntries = mutableMapOf<Long, ThreadBookmarkGroupEntry>()
        val orders = mutableMapOf<Long, Int>()

        threadBookmarkGroupToCreate.entries.forEach { threadBookmarkGroupEntryToCreate ->
          val databaseId = threadBookmarkGroupEntryToCreate.databaseId
          check(databaseId > 0L) { "Bad databaseId: $databaseId" }

          val ownerBookmarkId = threadBookmarkGroupEntryToCreate.ownerBookmarkId
          check(ownerBookmarkId > 0L) { "Bad ownerBookmarkId: $ownerBookmarkId" }

          threadBookmarkGroupEntries[databaseId] = ThreadBookmarkGroupEntry(
            databaseId = databaseId,
            ownerGroupId = groupId,
            ownerBookmarkId = ownerBookmarkId,
            threadDescriptor = threadBookmarkGroupEntryToCreate.threadDescriptor
          )

          orders[databaseId] = threadBookmarkGroupEntryToCreate.orderInGroup
        }

        if (!groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId] = ThreadBookmarkGroup(
            groupId = groupId,
            groupName = threadBookmarkGroupToCreate.groupName,
            isExpanded = threadBookmarkGroupToCreate.isExpanded,
            groupOrder = threadBookmarkGroupToCreate.groupOrder,
            entries = threadBookmarkGroupEntries,
            orders = threadBookmarkGroupToCreate.getEntryDatabaseIdsSorted().toMutableList()
          )
        } else {
          threadBookmarkGroupEntries.values.forEach { threadBookmarkGroupEntry ->
            val order = requireNotNull(orders[threadBookmarkGroupEntry.databaseId]) {
              "order not found by databaseId=${threadBookmarkGroupEntry.databaseId}"
            }

            groupsByGroupIdMap[groupId]?.addThreadBookmarkGroupEntry(
              threadBookmarkGroupEntry,
              order
            )
          }

          groupsByGroupIdMap[groupId]?.checkConsistency()
        }
      }

      return@withLock true
    }
  }

  suspend fun deleteGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    // Yes, we are locking the database access here, because we want this method to be atomic
    return mutex.withLock {
      val grouped = mutableMapOf<String, MutableList<ThreadBookmarkGroupEntry>>()
      val deleteTransaction = DeleteBookmarkGroupEntriesTransaction()

      for (bookmarkThreadDescriptor in bookmarkThreadDescriptors) {
        outer@
        for ((groupId, threadBookmarkGroup) in groupsByGroupIdMap) {
          var shouldBreak = false

          threadBookmarkGroup.iterateEntriesOrderedWhile { _, threadBookmarkGroupEntry ->
            if (threadBookmarkGroupEntry.threadDescriptor == bookmarkThreadDescriptor) {
              grouped.putIfNotContains(groupId, mutableListOf())
              grouped[groupId]!!.add(threadBookmarkGroupEntry)

              shouldBreak = true
              return@iterateEntriesOrderedWhile false
            }

            return@iterateEntriesOrderedWhile true
          }

          if (shouldBreak) {
            break@outer
          }
        }
      }

      grouped.forEach { (groupId, threadBookmarkGroupEntryList) ->
        threadBookmarkGroupEntryList.forEach { threadBookmarkGroupEntry ->
          groupsByGroupIdMap[groupId]?.removeThreadBookmarkGroupEntry(threadBookmarkGroupEntry)
        }

        deleteTransaction.toDelete.addAll(threadBookmarkGroupEntryList)

        val orderedList = mutableListOf<ThreadBookmarkGroupEntry>()
        groupsByGroupIdMap[groupId]?.iterateEntriesOrderedWhile { _, threadBookmarkGroupEntry ->
          orderedList += threadBookmarkGroupEntry
          return@iterateEntriesOrderedWhile true
        }

        deleteTransaction.toUpdate[groupId] = orderedList
      }

      if (deleteTransaction.isEmpty()) {
        return@withLock true
      }

      threadBookmarkGroupEntryRepository.executeDeleteTransaction(deleteTransaction)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error trying to delete bookmark group entries from the database", error)

          // Rollback the changes we did
          grouped.forEach { (groupId, threadBookmarkGroupEntryList) ->
            threadBookmarkGroupEntryList.forEach { threadBookmarkGroupEntry ->
              groupsByGroupIdMap[groupId]?.addThreadBookmarkGroupEntry(threadBookmarkGroupEntry)
            }

            groupsByGroupIdMap[groupId]?.checkConsistency()
          }

          return@withLock false
        }

      return@withLock true
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "ThreadBookmarkGroupEntryManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "ThreadBookmarkGroupEntryManager initialization completed, took $duration")
  }

  fun isReady() = suspendableInitializer.isInitialized()

  private suspend fun handleBookmarkChange(bookmarkChange: BookmarksManager.BookmarkChange) {
    if (bookmarkChange is BookmarksManager.BookmarkChange.BookmarksInitialized ||
      bookmarkChange is BookmarksManager.BookmarkChange.BookmarksUpdated) {
      return
    }

    awaitUntilInitialized()

    when (bookmarkChange) {
      BookmarksManager.BookmarkChange.BookmarksInitialized,
      is BookmarksManager.BookmarkChange.BookmarksUpdated -> return
      is BookmarksManager.BookmarkChange.BookmarksCreated -> {
        val threadDescriptors = bookmarkChange.threadDescriptors.toList()

        if (verboseLogs) {
          Logger.d(
            TAG,
            "New BookmarksCreated event, threadDescriptors count: ${threadDescriptors.size}"
          )
        }

        createGroupEntries(threadDescriptors)
      }
      is BookmarksManager.BookmarkChange.BookmarksDeleted -> {
        val threadDescriptors = bookmarkChange.threadDescriptors.toList()

        if (verboseLogs) {
          Logger.d(
            TAG,
            "New BookmarksDeleted event, threadDescriptors count: ${threadDescriptors.size}"
          )
        }

        deleteGroupEntries(threadDescriptors)
      }
    }
  }

  companion object {
    private const val TAG = "ThreadBookmarkGroupEntryManager"
  }

}