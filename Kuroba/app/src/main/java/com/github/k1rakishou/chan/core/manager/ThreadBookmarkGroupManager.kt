package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.features.bookmarks.data.GroupOfThreadBookmarkItemViews
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.CreateBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.DeleteBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntry
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntryToCreate
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupToCreate
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadBookmarkGroupManager(
  private val appScope: CoroutineScope,
  private val verboseLogs: Boolean,
  private val _threadBookmarkGroupEntryRepository: Lazy<ThreadBookmarkGroupRepository>,
  private val _bookmarksManager: Lazy<BookmarksManager>
) {
  private val mutex = Mutex()
  private val suspendableInitializer = SuspendableInitializer<Unit>("ThreadBookmarkGroupManager")

  @GuardedBy("mutex")
  // Map<GroupId, ThreadBookmarkGroup>
  private val groupsByGroupIdMap = mutableMapOf<String, ThreadBookmarkGroup>()

  private val threadBookmarkGroupEntryRepository: ThreadBookmarkGroupRepository
    get() = _threadBookmarkGroupEntryRepository.get()
  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()

  init {
    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .collect { bookmarkChange ->
          withContext(Dispatchers.Default) { handleBookmarkChange(bookmarkChange) }
        }
    }
  }

  fun initialize() {
    Logger.d(TAG, "ThreadBookmarkGroupManager.initialize()")

    appScope.launch(Dispatchers.IO) {
      Logger.d(TAG, "loadThreadBookmarkGroupsInternal() start")
      loadThreadBookmarkGroupsInternal()
      Logger.d(TAG, "loadThreadBookmarkGroupsInternal() end")
    }
  }

  private suspend fun loadThreadBookmarkGroupsInternal() {
    when (val groupsResult = threadBookmarkGroupEntryRepository.initialize()) {
      is ModularResult.Value -> {
        mutex.withLock {
          groupsByGroupIdMap.clear()

          groupsResult.value.forEach { threadBookmarkGroup ->
            groupsByGroupIdMap[threadBookmarkGroup.groupId] = threadBookmarkGroup
          }
        }

        suspendableInitializer.initWithValue(Unit)
        Logger.d(TAG, "loadThreadBookmarkGroupsInternal() done. Loaded ${groupsByGroupIdMap.size} bookmark groups")
      }
      is ModularResult.Error -> {
        suspendableInitializer.initWithError(groupsResult.error)
        Logger.e(TAG, "loadThreadBookmarkGroupsInternal() error", groupsResult.error)
      }
    }
  }

  suspend fun onBookmarkMoving(
    groupId: String,
    fromBookmarkDescriptor: ChanDescriptor.ThreadDescriptor,
    toBookmarkDescriptor: ChanDescriptor.ThreadDescriptor
  ): Boolean {
    return mutex.withLock {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLock false

      return@withLock group.moveBookmark(fromBookmarkDescriptor, toBookmarkDescriptor)
    }
  }

  suspend fun persistGroup(groupId: String) {
    mutex.withLock {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLock

      threadBookmarkGroupEntryRepository.updateGroup(group)
        .peekError { error -> Logger.e(TAG, "updateGroup(${groupId}) error", error) }
        .ignore()
    }
  }

  /**
   * Transforms an unordered list of bookmarks into an ordered list of groups of ordered bookmarks.
   * */
  suspend fun groupBookmarks(
    threadBookmarkViewList: List<ThreadBookmarkItemView>
  ): List<GroupOfThreadBookmarkItemViews> {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }

    if (threadBookmarkViewList.isEmpty()) {
      return emptyList()
    }

    return mutex.withLock {
      val groupIdSet = threadBookmarkViewList
        .map { threadBookmarkItemView -> threadBookmarkItemView.groupId }
        .toSet()
      val threadBookmarkViewMapByDescriptor = threadBookmarkViewList
        .associateBy { threadBookmarkView -> threadBookmarkView.threadDescriptor }

      val sortedGroups = groupIdSet
        .mapNotNull { groupId -> groupsByGroupIdMap[groupId] }
        .sortedBy { threadBookmarkGroup -> threadBookmarkGroup.groupOrder }

      val listOfGroups = mutableListWithCap<GroupOfThreadBookmarkItemViews>(sortedGroups.size)

      sortedGroups.forEach { threadBookmarkGroup ->
        val threadBookmarkItemViews =
          mutableListWithCap<ThreadBookmarkItemView>(threadBookmarkGroup.getEntriesCount())

        threadBookmarkGroup.iterateEntriesOrderedWhile { _, threadBookmarkGroupEntry ->
          val orderedThreadBookmarkItemView =
            threadBookmarkViewMapByDescriptor[threadBookmarkGroupEntry.threadDescriptor]

          if (orderedThreadBookmarkItemView != null) {
            threadBookmarkItemViews += orderedThreadBookmarkItemView
          }

          return@iterateEntriesOrderedWhile true
        }

        if (threadBookmarkItemViews.isEmpty()) {
          return@forEach
        }

        listOfGroups += GroupOfThreadBookmarkItemViews(
          groupId = threadBookmarkGroup.groupId,
          groupInfoText = createGroupInfoText(threadBookmarkGroup, threadBookmarkItemViews),
          isExpanded = threadBookmarkGroup.isExpanded,
          threadBookmarkItemViews = threadBookmarkItemViews
        )
      }

      return@withLock listOfGroups
    }
  }

  private fun createGroupInfoText(
    threadBookmarkGroup: ThreadBookmarkGroup,
    threadBookmarkItemViews: MutableList<ThreadBookmarkItemView>
  ): String {
    val totalBookmarksInGroupCount = threadBookmarkItemViews.size
    val watchingBookmarkInGroupCount = threadBookmarkItemViews
      .count { threadBookmarkItemView -> threadBookmarkItemView.threadBookmarkStats.watching }

    return String.format(
      Locale.ENGLISH,
      "${threadBookmarkGroup.groupName} (${watchingBookmarkInGroupCount}/${totalBookmarksInGroupCount})"
    )
  }

  /**
   * Toggles the bookmark's group expanded/collapsed state
   * */
  suspend fun toggleBookmarkExpandState(groupId: String): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }

    return mutex.withLock {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLock false

      val oldIsExpanded = group.isExpanded
      group.isExpanded = oldIsExpanded.not()

      threadBookmarkGroupEntryRepository.updateBookmarkGroupExpanded(groupId, group.isExpanded)
        .safeUnwrap { error ->
          groupsByGroupIdMap[groupId]?.isExpanded = oldIsExpanded

          Logger.e(TAG, "updateBookmarkGroupExpanded error", error)
          return@withLock false
        }

      return@withLock true
    }
  }

  /**
   * Creates new ThreadBookmarkGroupEntry for newly created ThreadBookmarks.
   * */
  private suspend fun createGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    // Yes, there is a database call inside of the locked block, but we need atomicity
    return mutex.withLock {
      val createTransaction = CreateBookmarkGroupEntriesTransaction()

      // 1. Create ThreadBookmarkGroup with ThreadBookmarkGroupToCreate and fill in createTransaction
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
            isExpanded = true,
            groupOrder = groupOrder,
            entries = mutableMapOf(),
            orders = mutableListOf()
          )
        }

        if (!createTransaction.toCreate.containsKey(groupId)) {
          createTransaction.toCreate[groupId] = ThreadBookmarkGroupToCreate(
            groupId = groupId,
            groupName = groupId,
            isExpanded = true,
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

      // 2. Try to insert everything into the database
      threadBookmarkGroupEntryRepository.executeCreateTransaction(createTransaction)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error trying to insert new bookmark group entries into the database", error)

          // Remove all databaseIds that == -1L from orders.
          createTransaction.toCreate.keys.forEach { groupId ->
            groupsByGroupIdMap[groupId]?.removeTemporaryOrders()
          }

          return@withLock false
        }

      // 3. Apply changes to the caches as well.
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

  /**
   * Deletes ThreadBookmarkGroupEntry from groupsByGroupIdMap and deletes them from the database
   * as well.
   * */
  private suspend fun deleteGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    // Yes, there is a database call inside of the locked block, but we need atomicity
    return mutex.withLock {
      val grouped = mutableMapOf<String, MutableList<ThreadBookmarkGroupEntry>>()
      val deleteTransaction = DeleteBookmarkGroupEntriesTransaction()

      // 1. Find ThreadBookmarkGroupEntry that we want to delete by their ThreadDescriptors
      for (bookmarkThreadDescriptor in bookmarkThreadDescriptors) {
        outer@
        for ((groupId, threadBookmarkGroup) in groupsByGroupIdMap) {
          var found = false

          threadBookmarkGroup.iterateEntriesOrderedWhile { _, threadBookmarkGroupEntry ->
            if (threadBookmarkGroupEntry.threadDescriptor == bookmarkThreadDescriptor) {
              grouped.putIfNotContains(groupId, mutableListOf())
              grouped[groupId]!!.add(threadBookmarkGroupEntry)

              found = true
              return@iterateEntriesOrderedWhile false
            }

            return@iterateEntriesOrderedWhile true
          }

          if (found) {
            break@outer
          }
        }
      }

      // 2. Remove the from the groupsByGroupIdMap + fill in the deleteTransaction
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

      // 3. Remove them from the database as well or roll everything back when we couldn't remove
      // something from the database
      threadBookmarkGroupEntryRepository.executeDeleteTransaction(deleteTransaction)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error trying to delete bookmark group entries from the database", error)

          // Rollback the changes we did upon errors
          grouped.forEach { (groupId, threadBookmarkGroupEntryList) ->
            threadBookmarkGroupEntryList.forEach { threadBookmarkGroupEntry ->
              // This is kinda bad, because it will ignore the original ordering and just insert
              // everything at the end of the "orders" list.
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
          Logger.d(TAG, "New BookmarksCreated event, threadDescriptors count: ${threadDescriptors.size}")
        }

        createGroupEntries(threadDescriptors)
      }
      is BookmarksManager.BookmarkChange.BookmarksDeleted -> {
        val threadDescriptors = bookmarkChange.threadDescriptors.toList()

        if (verboseLogs) {
          Logger.d(TAG, "New BookmarksDeleted event, threadDescriptors count: ${threadDescriptors.size}")
        }

        deleteGroupEntries(threadDescriptors)
      }
    }
  }

  companion object {
    private const val TAG = "ThreadBookmarkGroupEntryManager"
  }

}