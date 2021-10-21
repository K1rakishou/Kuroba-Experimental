package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.helper.OneShotRunnable
import com.github.k1rakishou.chan.features.bookmarks.data.GroupOfThreadBookmarkItemViews
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.move
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.CreateBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.DeleteBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.SimpleThreadBookmarkGroupToCreate
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
import java.util.regex.Pattern
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadBookmarkGroupManager(
  private val appScope: CoroutineScope,
  private val verboseLogs: Boolean,
  private val _threadBookmarkGroupRepository: Lazy<ThreadBookmarkGroupRepository>,
  private val _bookmarksManager: Lazy<BookmarksManager>
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  // Map<GroupId, ThreadBookmarkGroup>
  private val groupsByGroupIdMap = mutableMapOf<String, ThreadBookmarkGroup>()

  private val threadBookmarkGroupRepository: ThreadBookmarkGroupRepository
    get() = _threadBookmarkGroupRepository.get()
  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()

  private val initializationRunnable = OneShotRunnable()

  fun initialize() {
    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .collect { bookmarkChange ->
          withContext(Dispatchers.Default) { handleBookmarkChange(bookmarkChange) }
        }
    }
  }

  suspend fun viewBookmarkGroups(viewer: (ThreadBookmarkGroup) -> Unit) {
    ensureInitialized()

    mutex.withLock {
      groupsByGroupIdMap.values.forEach { threadBookmarkGroup ->
        viewer(threadBookmarkGroup)
      }
    }
  }

  suspend fun getBookmarkDescriptorsInGroup(groupId: String): List<ChanDescriptor.ThreadDescriptor> {
    ensureInitialized()

    return mutex.withLock { groupsByGroupIdMap[groupId]?.getBookmarkDescriptors() ?: emptyList() }
  }

  suspend fun onBookmarkGroupMoving(fromGroupId: String, toGroupId: String): Boolean {
    ensureInitialized()

    return mutex.withLock {
      if (groupsByGroupIdMap.isEmpty()) {
        return@withLock false
      }

      val groupIdAndOrderPairs = groupsByGroupIdMap.values
        .sortedBy { threadBookmarkGroup -> threadBookmarkGroup.groupOrder }
        .map { threadBookmarkGroup -> threadBookmarkGroup.groupId to threadBookmarkGroup.groupOrder }
        .toMutableList()

      val fromGroupIdIndex = groupIdAndOrderPairs.indexOfFirst { (groupId, _) -> groupId == fromGroupId }
      val toGroupIdIndex = groupIdAndOrderPairs.indexOfFirst { (groupId, _) -> groupId == toGroupId }

      if (fromGroupIdIndex < 0 || toGroupIdIndex < 0 || fromGroupIdIndex == toGroupIdIndex) {
        return@withLock false
      }

      groupIdAndOrderPairs.move(fromGroupIdIndex, toGroupIdIndex)

      groupIdAndOrderPairs.forEachIndexed { newOrder, groupIdAndOrderPair ->
        val groupId = groupIdAndOrderPair.first
        groupsByGroupIdMap[groupId]?.groupOrder = newOrder
      }

      return@withLock true
    }
  }

  suspend fun onBookmarkMoving(
    groupId: String,
    fromBookmarkDescriptor: ChanDescriptor.ThreadDescriptor,
    toBookmarkDescriptor: ChanDescriptor.ThreadDescriptor
  ): Boolean {
    ensureInitialized()

    return mutex.withLock {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLock false

      return@withLock group.moveBookmark(fromBookmarkDescriptor, toBookmarkDescriptor)
    }
  }

  suspend fun moveBookmarksFromGroupToGroup(
    bookmarksToMove: List<ChanDescriptor.ThreadDescriptor>,
    destGroupId: String
  ): ModularResult<Boolean> {
    ensureInitialized()

    return ModularResult.Try {
      if (bookmarksToMove.isEmpty()) {
        return@Try true
      }

      val successfullyMovedBookmarkDescriptors = mutableListWithCap<ChanDescriptor.ThreadDescriptor>(bookmarksToMove.size)

      val allSucceeded = mutex.withLock {
        val destGroup = groupsByGroupIdMap[destGroupId]
          ?: return@withLock true

        val bookmarksToMoveSet = bookmarksToMove.toSet()
        var allSucceeded = true

        groupsByGroupIdMap.entries.forEach { (groupId, threadBookmarkGroup) ->
          if (groupId == destGroupId) {
            return@forEach
          }

          val deleteTransaction = DeleteBookmarkGroupEntriesTransaction()
          val threadBookmarkGroupEntryList = mutableListOf<ThreadBookmarkGroupEntry>()

          threadBookmarkGroup.iterateEntriesOrderedWhile { _, threadBookmarkGroupEntry ->
            if (threadBookmarkGroupEntry.threadDescriptor in bookmarksToMoveSet) {
              deleteTransaction.toDelete.add(threadBookmarkGroupEntry)
              threadBookmarkGroupEntryList += threadBookmarkGroupEntry
            }

            return@iterateEntriesOrderedWhile true
          }

          val createTransaction = CreateBookmarkGroupEntriesTransaction()
          createTransaction.toCreate[destGroupId] = ThreadBookmarkGroupToCreate(
            groupId = destGroup.groupId,
            groupName = destGroup.groupName,
            isExpanded = destGroup.isExpanded,
            groupOrder = destGroup.groupOrder,
            entries = threadBookmarkGroupEntryList
              .map { threadBookmarkGroupEntry ->
                return@map ThreadBookmarkGroupEntryToCreate(
                  ownerGroupId = destGroup.groupId,
                  threadDescriptor = threadBookmarkGroupEntry.threadDescriptor,
                  orderInGroup = destGroup.reserveSpaceForBookmarkOrder(),
                )
              }.toMutableList()
          )

          threadBookmarkGroupRepository.executeCreateTransaction(createTransaction)
            .safeUnwrap { error ->
              Logger.e(TAG, "Error trying to insert new bookmark group entries into the database", error)
              allSucceeded = false

              // Remove all databaseIds that == -1L from orders.
              createTransaction.toCreate.keys.forEach { groupId ->
                groupsByGroupIdMap[groupId]?.removeTemporaryOrders()
              }

              return@forEach
            }

          threadBookmarkGroupRepository.executeDeleteTransaction(deleteTransaction)
            .safeUnwrap { error ->
              Logger.e(TAG, "Error trying to delete bookmark group entries from the database", error)
              allSucceeded = false

              // Remove all databaseIds that == -1L from orders.
              createTransaction.toCreate.keys.forEach { groupId ->
                groupsByGroupIdMap[groupId]?.removeTemporaryOrders()
              }

              return@forEach
            }

          deleteTransaction.toDelete.forEach { threadBookmarkGroupEntry ->
            groupsByGroupIdMap[threadBookmarkGroup.groupId]
              ?.removeThreadBookmarkGroupEntry(threadBookmarkGroupEntry)
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
              successfullyMovedBookmarkDescriptors.add(threadBookmarkGroupEntryToCreate.threadDescriptor)
            }

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

        return@withLock allSucceeded
      }

      if (successfullyMovedBookmarkDescriptors.isNotEmpty()) {
        bookmarksManager.updateBookmarksNoPersist(
          threadDescriptors = successfullyMovedBookmarkDescriptors,
          mutator = { threadBookmark -> threadBookmark.groupId = destGroupId }
        )

        bookmarksManager.persistBookmarksManually(successfullyMovedBookmarkDescriptors)
      }

      return@Try allSucceeded
    }
  }

  suspend fun removeBookmarkGroup(groupId: String): ModularResult<Boolean> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLock {
        if (groupId == DEFAULT_GROUP_ID) {
          return@withLock false
        }

        threadBookmarkGroupRepository.deleteBookmarkGroup(groupId)
          .unwrap()

        groupsByGroupIdMap.remove(groupId)

        return@withLock true
      }
    }
  }

  suspend fun persistGroup(groupId: String) {
    ensureInitialized()

    mutex.withLock {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLock

      threadBookmarkGroupRepository.updateGroup(group)
        .peekError { error -> Logger.e(TAG, "updateGroup(${groupId}) error", error) }
        .ignore()
    }
  }

  suspend fun updateGroupOrders() {
    ensureInitialized()

    mutex.withLock {
      val groups = groupsByGroupIdMap.values.toList()
      if (groups.isEmpty()) {
        return@withLock
      }

      threadBookmarkGroupRepository.updateGroupOrders(groups)
        .peekError { error -> Logger.e(TAG, "updateGroups() error", error) }
        .ignore()
    }
  }

  suspend fun groupAlreadyExists(groupName: String): ModularResult<Boolean> {
    ensureInitialized()

    return ModularResult.Try {
      val groupId = rawGroupNameToGroupId(groupName)
      if (groupId == null) {
        throw GroupCreationError("Invalid group name: \'$groupName\' (empty or blank)")
      }

      return@Try mutex.withLock { groupsByGroupIdMap.containsKey(groupId) }
    }
  }

  suspend fun createBookmarkGroup(groupName: String): ModularResult<Unit> {
    ensureInitialized()

    return ModularResult.Try {
      val groupId = rawGroupNameToGroupId(groupName)
      if (groupId == null) {
        throw GroupCreationError("Invalid group name: \'$groupName\' (empty or blank)")
      }

      mutex.withLock {
        val prevGroup = groupsByGroupIdMap[groupId]
        if (prevGroup != null) {
          throw GroupCreationError("Group with id \'$groupId\' already exists")
        }

        val createTransaction = CreateBookmarkGroupEntriesTransaction()
        val groupOrder = getNextGroupOrder()

        createTransaction.toCreate[groupId] = ThreadBookmarkGroupToCreate(
          groupId = groupId,
          groupName = groupName,
          isExpanded = true,
          groupOrder = groupOrder,
          entries = mutableListOf()
        )

        threadBookmarkGroupRepository.executeCreateTransaction(createTransaction)
          .peekError { error -> Logger.e(TAG, "Error trying to create new bookmark group", error) }
          .unwrap()

        groupsByGroupIdMap[groupId] = ThreadBookmarkGroup(
          groupId = groupId,
          groupName = groupName,
          isExpanded = true,
          groupOrder = groupOrder,
          entries = mutableMapOf(),
          orders = mutableListOf()
        )
      }
    }
  }

  /**
   * Transforms an unordered list of bookmarks into an ordered list of groups of ordered bookmarks.
   * */
  suspend fun groupBookmarks(
    threadBookmarkViewList: List<ThreadBookmarkItemView>,
    bookmarksToHighlight: Set<ChanDescriptor.ThreadDescriptor>
  ): List<GroupOfThreadBookmarkItemViews> {
    ensureInitialized()

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

        var shouldGroupBeExpanded = threadBookmarkGroup.isExpanded

        threadBookmarkGroup.iterateEntriesOrderedWhile { _, threadBookmarkGroupEntry ->
          val orderedThreadBookmarkItemView =
            threadBookmarkViewMapByDescriptor[threadBookmarkGroupEntry.threadDescriptor]

          if (orderedThreadBookmarkItemView != null) {
            threadBookmarkItemViews += orderedThreadBookmarkItemView
          }

          if (threadBookmarkGroupEntry.threadDescriptor in bookmarksToHighlight) {
            shouldGroupBeExpanded = true
          }

          return@iterateEntriesOrderedWhile true
        }

        if (threadBookmarkItemViews.isEmpty()) {
          return@forEach
        }

        listOfGroups += GroupOfThreadBookmarkItemViews(
          groupId = threadBookmarkGroup.groupId,
          groupInfoText = createGroupInfoText(threadBookmarkGroup, threadBookmarkItemViews),
          isExpanded = shouldGroupBeExpanded,
          threadBookmarkItemViews = threadBookmarkItemViews
        )
      }

      return@withLock listOfGroups
    }
  }

  /**
   * Toggles the bookmark's group expanded/collapsed state
   * */
  suspend fun toggleBookmarkExpandState(groupId: String): Boolean {
    ensureInitialized()

    return mutex.withLock {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLock false

      val oldIsExpanded = group.isExpanded
      group.isExpanded = oldIsExpanded.not()

      threadBookmarkGroupRepository.updateBookmarkGroupExpanded(groupId, group.isExpanded)
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
  suspend fun createGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    ensureInitialized()
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    // Yes, there is a database call inside of the locked block, but we need atomicity
    return mutex.withLock {
      val createTransaction = CreateBookmarkGroupEntriesTransaction()

      // 1. Create ThreadBookmarkGroup with ThreadBookmarkGroupToCreate and fill in createTransaction
      bookmarksManager.viewBookmarks(bookmarkThreadDescriptors) { threadBookmarkView ->
        val groupId = threadBookmarkView.groupId ?: DEFAULT_GROUP_ID
        val groupName = if (threadBookmarkView.groupId == null) {
          DEFAULT_GROUP_NAME
        } else {
          threadBookmarkView.groupId!!
        }

        val groupOrder = if (groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId]!!.groupOrder
        } else {
          getNextGroupOrder()
        }

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
            groupName = groupName,
            isExpanded = true,
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

      return@withLock createNewGroupsInternal(createTransaction)
    }
  }

  suspend fun createNewGroupEntries(bookmarkGroupsToCreate: List<SimpleThreadBookmarkGroupToCreate>) {
    if (bookmarkGroupsToCreate.isEmpty()) {
      return
    }

    ensureInitialized()

    mutex.withLock {
      val createTransaction = CreateBookmarkGroupEntriesTransaction()

      bookmarkGroupsToCreate.forEach { bookmarkGroupToCreate ->
        val groupName = bookmarkGroupToCreate.groupName
        val groupId = rawGroupNameToGroupId(groupName)
          ?: return@forEach

        val groupOrder = if (groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId]!!.groupOrder
        } else {
          getNextGroupOrder()
        }

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

        val threadBookmarkGroup = groupsByGroupIdMap[groupId]!!

        createTransaction.toCreate[groupId] = ThreadBookmarkGroupToCreate(
          groupId = groupId,
          groupName = groupName,
          isExpanded = true,
          groupOrder = groupOrder,
          entries = bookmarkGroupToCreate.entries.map { threadDescriptor ->
            ThreadBookmarkGroupEntryToCreate(
              ownerGroupId = groupId,
              threadDescriptor = threadDescriptor,
              orderInGroup = threadBookmarkGroup.reserveSpaceForBookmarkOrder()
            )
          }.toMutableList()
        )

        if (createTransaction.isEmpty()) {
          return@forEach
        }

        createNewGroupsInternal(createTransaction)
      }
    }
  }

  private suspend fun createNewGroupsInternal(
    createTransaction: CreateBookmarkGroupEntriesTransaction
  ): Boolean {
    require(mutex.isLocked) { "Mutex is not locked!" }

    // Try to insert everything into the database
    threadBookmarkGroupRepository.executeCreateTransaction(createTransaction)
      .safeUnwrap { error ->
        Logger.e(TAG, "Error trying to insert new bookmark group entries into the database", error)

        // Remove all databaseIds that == -1L from orders.
        createTransaction.toCreate.keys.forEach { groupId ->
          groupsByGroupIdMap[groupId]?.removeTemporaryOrders()
        }

        return false
      }

    // Apply changes to the caches as well.
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

    return true
  }

    /**
   * Deletes ThreadBookmarkGroupEntry from groupsByGroupIdMap and deletes them from the database
   * as well.
   * */
  private suspend fun deleteGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    ensureInitialized()
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
      threadBookmarkGroupRepository.executeDeleteTransaction(deleteTransaction)
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

  private fun getNextGroupOrder(): Int {
    require(mutex.isLocked) { "Mutex is not locked!" }

    return groupsByGroupIdMap.values
      .maxOfOrNull { threadBookmarkGroup -> threadBookmarkGroup.groupOrder }
      ?.plus(1) ?: 0
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

  private suspend fun handleBookmarkChange(bookmarkChange: BookmarksManager.BookmarkChange) {
    ensureInitialized()

    if (bookmarkChange is BookmarksManager.BookmarkChange.BookmarksInitialized ||
      bookmarkChange is BookmarksManager.BookmarkChange.BookmarksUpdated) {
      return
    }

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

  private suspend fun ensureInitialized() {
    initializationRunnable.runIfNotYet { loadThreadBookmarkGroupsInternal() }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun loadThreadBookmarkGroupsInternal() {
    withContext(Dispatchers.IO) {
      Logger.d(TAG, "loadThreadBookmarkGroupsInternal() start")

      val time = measureTime {
        when (val groupsResult = threadBookmarkGroupRepository.initialize()) {
          is ModularResult.Value -> {
            mutex.withLock {
              groupsByGroupIdMap.clear()

              groupsResult.value.forEach { threadBookmarkGroup ->
                groupsByGroupIdMap[threadBookmarkGroup.groupId] = threadBookmarkGroup
              }

              // Pre-create the default groups (for now the Default where all bookmarks are moved
              // by default and Filter watcher that is used by the filter watcher)
              createDefaultGroupsIfNeeded()
            }

            Logger.d(TAG, "loadThreadBookmarkGroupsInternal() done. Loaded ${groupsByGroupIdMap.size} bookmark groups")
          }
          is ModularResult.Error -> {
            Logger.e(TAG, "loadThreadBookmarkGroupsInternal() error", groupsResult.error)
          }
        }
      }

      Logger.d(TAG, "loadThreadBookmarkGroupsInternal() end, took $time")
    }
  }

  private suspend fun createDefaultGroupsIfNeeded() {
    require(mutex.isLocked) { "Mutex is not locked!" }

    val createTransaction = CreateBookmarkGroupEntriesTransaction()
    var maxOrder = groupsByGroupIdMap.values
      .maxOfOrNull { threadBookmarkGroup -> threadBookmarkGroup.groupOrder } ?: 0

    if (!groupsByGroupIdMap.containsKey(DEFAULT_GROUP_ID)) {
      createTransaction.toCreate[DEFAULT_GROUP_ID] = ThreadBookmarkGroupToCreate(
        groupId = DEFAULT_GROUP_ID,
        groupName = DEFAULT_GROUP_NAME,
        isExpanded = true,
        groupOrder = maxOrder++,
        entries = mutableListOf()
      )
    }

    if (!groupsByGroupIdMap.containsKey(FILTER_WATCHER_GROUP_ID)) {
      createTransaction.toCreate[FILTER_WATCHER_GROUP_ID] = ThreadBookmarkGroupToCreate(
        groupId = FILTER_WATCHER_GROUP_ID,
        groupName = FILTER_WATCHER_GROUP_NAME,
        isExpanded = true,
        groupOrder = maxOrder++,
        entries = mutableListOf()
      )
    }

    if (createTransaction.isEmpty()) {
      return
    }

    threadBookmarkGroupRepository.executeCreateTransaction(createTransaction)
      .peekError { error -> Logger.e(TAG, "Error trying to create new bookmark group", error) }
      .unwrap()

    createTransaction.toCreate.entries.forEach { (groupId, threadBookmarkGroupToCreate) ->
      val defaultGroup = ThreadBookmarkGroup(
        groupId = groupId,
        groupName = threadBookmarkGroupToCreate.groupName,
        isExpanded = threadBookmarkGroupToCreate.isExpanded,
        groupOrder = threadBookmarkGroupToCreate.groupOrder,
        entries = mutableMapOf(),
        orders = mutableListOf()
      )

      groupsByGroupIdMap[groupId] = defaultGroup
    }
  }

  class GroupCreationError(message: String) : Exception(message)

  companion object {
    private const val TAG = "ThreadBookmarkGroupManager"

    private val WHITESPACE_PATTER = Pattern.compile("\\s+").toRegex()

    const val DEFAULT_GROUP_ID = "default_group"
    const val FILTER_WATCHER_GROUP_ID = "filter_watcher"
    private const val DEFAULT_GROUP_NAME = "Default group"
    private const val FILTER_WATCHER_GROUP_NAME = "Filter watcher group"

    fun isDefaultGroup(groupId: String): Boolean {
      return groupId == DEFAULT_GROUP_ID || groupId == FILTER_WATCHER_GROUP_ID
    }

    fun rawGroupNameToGroupId(groupName: String): String? {
      val groupId = groupName.trim().replace(WHITESPACE_PATTER, "_")
      if (groupId.isBlank()) {
        return null
      }

      return groupId
    }
  }

}