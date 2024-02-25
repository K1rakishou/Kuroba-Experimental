package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.helper.OneShotRunnable
import com.github.k1rakishou.chan.core.usecase.GetThreadBookmarkGroupIdsUseCase
import com.github.k1rakishou.chan.features.bookmarks.data.GroupOfThreadBookmarkItemViews
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.move
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.withLockNonCancellable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.BookmarkGroupMatchFlag
import com.github.k1rakishou.model.data.bookmark.CreateBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.DeleteBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.SimpleThreadBookmarkGroupToCreate
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntry
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupEntryToCreate
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupMatchPattern
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupToCreate
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadBookmarkGroupManager(
  private val appScope: CoroutineScope,
  private val verboseLogs: Boolean,
  private val _threadBookmarkGroupRepository: Lazy<ThreadBookmarkGroupRepository>,
  private val _bookmarksManager: Lazy<BookmarksManager>,
  private val _getThreadBookmarkGroupIdsUseCase: Lazy<GetThreadBookmarkGroupIdsUseCase>
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  // Map<GroupId, ThreadBookmarkGroup>
  private val groupsByGroupIdMap = mutableMapOf<String, ThreadBookmarkGroup>()

  private val threadBookmarkGroupRepository: ThreadBookmarkGroupRepository
    get() = _threadBookmarkGroupRepository.get()
  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  private val getThreadBookmarkGroupIdsUseCase: GetThreadBookmarkGroupIdsUseCase
    get() = _getThreadBookmarkGroupIdsUseCase.get()

  private val initializationRunnable = OneShotRunnable()

  fun initialize() {
    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .collect { bookmarkChange ->
          withContext(Dispatchers.Default) { handleBookmarkChange(bookmarkChange) }
        }
    }
  }

  suspend fun getMatchingGroupIdWithName(
    boardDescriptor: BoardDescriptor,
    postSubject: CharSequence,
    postComment: CharSequence
  ): GroupIdWithName {
    ensureInitialized()

    return mutex.withLockNonCancellable {
      val threadBookmarkGroupEntriesSorted = groupsByGroupIdMap.entries
        .sortedBy { (_, threadBookmarkGroup) -> threadBookmarkGroup.groupOrder }

      for ((groupId, threadBookmarkGroup) in threadBookmarkGroupEntriesSorted) {
        if (threadBookmarkGroup.isDefaultGroup()) {
          // Skip the default group here since it always matches everything and will be used
          // if no other group matches this bookmark info
          continue
        }

        if (threadBookmarkGroup.matches(boardDescriptor, postSubject, postComment)) {
          return@withLockNonCancellable GroupIdWithName(
            groupId = groupId,
            groupName = threadBookmarkGroup.groupName
          )
        }
      }

      return@withLockNonCancellable GroupIdWithName(
        groupId = ThreadBookmarkGroup.DEFAULT_GROUP_ID,
        groupName = ThreadBookmarkGroup.DEFAULT_GROUP_NAME
      )
    }
  }

  suspend fun contains(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    ensureInitialized()

    return mutex.withLockNonCancellable {
      return@withLockNonCancellable groupsByGroupIdMap.values
        .any { threadBookmarkGroup -> threadBookmarkGroup.contains(threadDescriptor) }
    }
  }

  suspend fun getMatchingPattern(bookmarkGroupId: String): ThreadBookmarkGroupMatchPattern? {
    ensureInitialized()

    return mutex.withLockNonCancellable { groupsByGroupIdMap[bookmarkGroupId]?.matchingPattern }
  }

  suspend fun viewBookmarkGroupsOrdered(viewer: (ThreadBookmarkGroup) -> Unit) {
    ensureInitialized()

    mutex.withLockNonCancellable {
      val threadBookmarkGroupEntriesOrdered = groupsByGroupIdMap.entries
        .sortedBy { (_, threadBookmarkGroup) -> threadBookmarkGroup.groupOrder }

      threadBookmarkGroupEntriesOrdered.forEach { (_, threadBookmarkGroup) ->
        viewer(threadBookmarkGroup)
      }
    }
  }

  suspend fun getBookmarkDescriptorsInGroup(groupId: String): List<ChanDescriptor.ThreadDescriptor> {
    ensureInitialized()

    return mutex.withLockNonCancellable { groupsByGroupIdMap[groupId]?.getBookmarkDescriptors() ?: emptyList() }
  }

  suspend fun onBookmarkGroupMoving(fromGroupId: String, toGroupId: String): Boolean {
    ensureInitialized()

    return mutex.withLockNonCancellable {
      if (groupsByGroupIdMap.isEmpty()) {
        return@withLockNonCancellable false
      }

      val groupIdAndOrderPairs = groupsByGroupIdMap.values
        .sortedBy { threadBookmarkGroup -> threadBookmarkGroup.groupOrder }
        .map { threadBookmarkGroup -> threadBookmarkGroup.groupId to threadBookmarkGroup.groupOrder }
        .toMutableList()

      val fromGroupIdIndex = groupIdAndOrderPairs.indexOfFirst { (groupId, _) -> groupId == fromGroupId }
      val toGroupIdIndex = groupIdAndOrderPairs.indexOfFirst { (groupId, _) -> groupId == toGroupId }

      if (fromGroupIdIndex < 0 || toGroupIdIndex < 0 || fromGroupIdIndex == toGroupIdIndex) {
        return@withLockNonCancellable false
      }

      groupIdAndOrderPairs.move(fromGroupIdIndex, toGroupIdIndex)

      groupIdAndOrderPairs.forEachIndexed { newOrder, groupIdAndOrderPair ->
        val groupId = groupIdAndOrderPair.first
        groupsByGroupIdMap[groupId]?.groupOrder = newOrder
      }

      return@withLockNonCancellable true
    }
  }

  suspend fun onBookmarkMoving(
    groupId: String,
    fromBookmarkDescriptor: ChanDescriptor.ThreadDescriptor,
    toBookmarkDescriptor: ChanDescriptor.ThreadDescriptor
  ): Boolean {
    ensureInitialized()

    return mutex.withLockNonCancellable {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLockNonCancellable false

      return@withLockNonCancellable group.moveBookmark(fromBookmarkDescriptor, toBookmarkDescriptor)
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

      val successfullyMovedBookmarkDescriptors =
        mutableListWithCap<ChanDescriptor.ThreadDescriptor>(bookmarksToMove.size)

      val allSucceeded = mutex.withLockNonCancellable {
        val destGroup = groupsByGroupIdMap[destGroupId]
          ?: return@withLockNonCancellable true

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

          val reserveDBId = ThreadBookmarkGroup.nextReserveDBId()
          val createTransaction = CreateBookmarkGroupEntriesTransaction()

          createTransaction.toCreate[destGroupId] = ThreadBookmarkGroupToCreate(
            reserveDBId = reserveDBId,
            groupId = destGroup.groupId,
            groupName = destGroup.groupName,
            isExpanded = destGroup.isExpanded,
            groupOrder = destGroup.groupOrder,
            matchingPattern = destGroup.matchingPattern,
            entries = threadBookmarkGroupEntryList
              .map { threadBookmarkGroupEntry ->
                return@map ThreadBookmarkGroupEntryToCreate(
                  ownerGroupId = destGroup.groupId,
                  threadDescriptor = threadBookmarkGroupEntry.threadDescriptor,
                  orderInGroup = destGroup.reserveSpaceForBookmarkOrder(reserveDBId)
                )
              }.toMutableList()
          )

          threadBookmarkGroupRepository.executeCreateAndDeleteTransactions(
            createTransaction = createTransaction,
            deleteTransaction = deleteTransaction
          )
            .safeUnwrap { error ->
              Logger.e(TAG, "Error trying to insert new bookmark group entries into the database", error)
              allSucceeded = false

              createTransaction.toCreate.entries.forEach { (groupId, threadBookmarkGroupToCreate) ->
                groupsByGroupIdMap[groupId]?.removeTemporaryOrders(threadBookmarkGroupToCreate.reserveDBId)
              }

              return@forEach
            }

          deleteTransaction.toDelete.forEach { threadBookmarkGroupEntry ->
            val bookmarkGroupId = threadBookmarkGroup.groupId

            groupsByGroupIdMap[bookmarkGroupId]
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
                reserveDBId = threadBookmarkGroupToCreate.reserveDBId,
                threadBookmarkGroupEntry = threadBookmarkGroupEntry,
                orderInGroup = order
              )
            }

            groupsByGroupIdMap[groupId]?.checkConsistency()
          }
        }

        return@withLockNonCancellable allSucceeded
      }

      return@Try allSucceeded
    }
  }

  suspend fun updateGroupMatcherPattern(
    groupId: String,
    matchFlag: BookmarkGroupMatchFlag
  ): ModularResult<Boolean> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLockNonCancellable {
        val threadBookmarkGroup = groupsByGroupIdMap[groupId]
          ?: return@withLockNonCancellable false

        threadBookmarkGroup.updateMatchingPattern(ThreadBookmarkGroupMatchPattern(matchFlag))

        threadBookmarkGroupRepository.updateGroup(threadBookmarkGroup)
          .onError { error -> Logger.e(TAG, "updateGroupMatcherPattern() updateGroup(${groupId}) error", error) }
          .unwrap()

        return@withLockNonCancellable true
      }
    }
  }

  suspend fun removeBookmarkGroup(groupId: String): ModularResult<Boolean> {
    ensureInitialized()

    return ModularResult.Try {
      return@Try mutex.withLockNonCancellable {
        if (ThreadBookmarkGroup.isDefaultGroup(groupId)) {
          return@withLockNonCancellable false
        }

        threadBookmarkGroupRepository.deleteBookmarkGroup(groupId)
          .unwrap()

        groupsByGroupIdMap.remove(groupId)

        return@withLockNonCancellable true
      }
    }
  }

  suspend fun persistGroup(groupId: String) {
    ensureInitialized()

    mutex.withLockNonCancellable {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLockNonCancellable

      threadBookmarkGroupRepository.updateGroup(group)
        .onError { error -> Logger.e(TAG, "updateGroup(${groupId}) error", error) }
        .ignore()

      threadBookmarkGroupRepository.updateGroupEntries(listOf(group))
        .onError { error -> Logger.e(TAG, "updateGroupEntries(${groupId}) error", error) }
        .ignore()
    }
  }

  suspend fun updateGroupOrders() {
    ensureInitialized()

    mutex.withLockNonCancellable {
      val groups = groupsByGroupIdMap.values.toList()
      if (groups.isEmpty()) {
        return@withLockNonCancellable
      }

      threadBookmarkGroupRepository.updateGroups(groups)
        .onError { error -> Logger.e(TAG, "updateGroups() error", error) }
        .ignore()
    }
  }

  suspend fun existingGroupIdAndName(groupName: String): ModularResult<GroupIdWithName?> {
    ensureInitialized()

    return ModularResult.Try {
      val groupId = ThreadBookmarkGroup.rawGroupNameToGroupId(groupName)
      if (groupId == null) {
        throw GroupCreationError("Invalid group name: \'$groupName\' (empty or blank)")
      }

      return@Try mutex.withLockNonCancellable {
        val existingGroup = groupsByGroupIdMap[groupId]
          ?: return@withLockNonCancellable null

        return@withLockNonCancellable GroupIdWithName(
          groupId = existingGroup.groupId,
          groupName = existingGroup.groupName
        )
      }
    }
  }

  suspend fun createBookmarkGroup(groupName: String): ModularResult<Unit> {
    ensureInitialized()

    return ModularResult.Try {
      val groupId = ThreadBookmarkGroup.rawGroupNameToGroupId(groupName)
      if (groupId == null) {
        throw GroupCreationError("Invalid group name: \'$groupName\' (empty or blank)")
      }

      mutex.withLockNonCancellable {
        val prevGroup = groupsByGroupIdMap[groupId]
        if (prevGroup != null) {
          throw GroupCreationError("Group with id \'$groupId\' already exists")
        }

        val createTransaction = CreateBookmarkGroupEntriesTransaction()
        val groupOrder = getNextGroupOrder()

        createTransaction.toCreate[groupId] = ThreadBookmarkGroupToCreate(
          reserveDBId = -1,
          groupId = groupId,
          groupName = groupName,
          isExpanded = true,
          groupOrder = groupOrder,
          matchingPattern = null
        )

        threadBookmarkGroupRepository.executeCreateTransaction(createTransaction)
          .onError { error -> Logger.e(TAG, "Error trying to create new bookmark group", error) }
          .unwrap()

        groupsByGroupIdMap[groupId] = ThreadBookmarkGroup(
          groupId = groupId,
          groupName = groupName,
          isExpanded = true,
          groupOrder = groupOrder,
          newMatchingPattern = null
        )
      }
    }
  }

  /**
   * Transforms an unordered list of bookmarks into an ordered list of groups of ordered bookmarks.
   * */
  suspend fun groupBookmarks(
    threadBookmarkViewList: List<ThreadBookmarkItemView>,
    bookmarksToHighlight: Set<ChanDescriptor.ThreadDescriptor>,
    hasSearchQuery: Boolean
  ): List<GroupOfThreadBookmarkItemViews> {
    ensureInitialized()

    if (threadBookmarkViewList.isEmpty()) {
      return emptyList()
    }

    return mutex.withLockNonCancellable {
      val groupIdSet =  getGroupIdSetByThreadDescriptor(
        threadDescriptors = threadBookmarkViewList
          .map { threadBookmarkView -> threadBookmarkView.threadDescriptor }
      )

      val threadBookmarkViewMapByDescriptor = threadBookmarkViewList
        .associateBy { threadBookmarkView -> threadBookmarkView.threadDescriptor }

      val sortedGroups = groupIdSet
        .mapNotNull { groupId -> groupsByGroupIdMap[groupId] }
        .sortedBy { threadBookmarkGroup -> threadBookmarkGroup.groupOrder }

      val listOfGroups = mutableListWithCap<GroupOfThreadBookmarkItemViews>(sortedGroups.size)

      sortedGroups.forEach { threadBookmarkGroup ->
        val threadBookmarkItemViews =
          mutableListWithCap<ThreadBookmarkItemView>(threadBookmarkGroup.getEntriesCount())

        var shouldGroupBeExpanded = threadBookmarkGroup.isExpanded || hasSearchQuery

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

      return@withLockNonCancellable listOfGroups
    }
  }

  /**
   * Toggles the bookmark's group expanded/collapsed state
   * */
  suspend fun toggleBookmarkExpandState(groupId: String): Boolean {
    ensureInitialized()

    return mutex.withLockNonCancellable {
      val group = groupsByGroupIdMap[groupId]
        ?: return@withLockNonCancellable false

      val oldIsExpanded = group.isExpanded
      group.isExpanded = oldIsExpanded.not()

      threadBookmarkGroupRepository.updateBookmarkGroupExpanded(groupId, group.isExpanded)
        .safeUnwrap { error ->
          groupsByGroupIdMap[groupId]?.isExpanded = oldIsExpanded

          Logger.e(TAG, "updateBookmarkGroupExpanded error", error)
          return@withLockNonCancellable false
        }

      return@withLockNonCancellable true
    }
  }

  /**
   * Creates new ThreadBookmarkGroupEntry for newly created ThreadBookmarks.
   * */
  suspend fun createGroupEntries(
    bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>,
    forceDefaultGroup: Boolean
  ): Boolean {
    ensureInitialized()
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    val matchedGroupsMap = if (forceDefaultGroup) {
      emptyMap()
    } else {
      val parameters = GetThreadBookmarkGroupIdsUseCase.Parameters(bookmarkThreadDescriptors)
      getThreadBookmarkGroupIdsUseCase.execute(parameters).groupsMap
    }

    // Yes, there is a database call inside of the locked block, but we need atomicity
    return mutex.withLockNonCancellable {
      val createTransaction = CreateBookmarkGroupEntriesTransaction()

      // 1. Create ThreadBookmarkGroup with ThreadBookmarkGroupToCreate and fill in createTransaction
      bookmarksManager.viewBookmarks(bookmarkThreadDescriptors) { threadBookmarkView ->
        val threadDescriptor = threadBookmarkView.threadDescriptor

        val containsBookmarkEntry = groupsByGroupIdMap
          .values
          .any { threadBookmarkGroup -> threadBookmarkGroup.contains(threadDescriptor) }

        if (containsBookmarkEntry) {
          return@viewBookmarks
        }

        val groupId = matchedGroupsMap[threadDescriptor]?.groupId
          ?: ThreadBookmarkGroup.DEFAULT_GROUP_ID
        val groupName = matchedGroupsMap[threadDescriptor]?.groupName
          ?: ThreadBookmarkGroup.DEFAULT_GROUP_NAME

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
            newMatchingPattern = null
          )
        }

        if (!createTransaction.toCreate.containsKey(groupId)) {
          val reserveDBId = ThreadBookmarkGroup.nextReserveDBId()

          createTransaction.toCreate[groupId] = ThreadBookmarkGroupToCreate(
            reserveDBId = reserveDBId,
            groupId = groupId,
            groupName = groupName,
            isExpanded = true,
            groupOrder = groupOrder,
            matchingPattern = null
          )
        }

        val reserveDBId = createTransaction.toCreate[groupId]!!.reserveDBId
        val newBookmarkOrder = groupsByGroupIdMap[groupId]!!.reserveSpaceForBookmarkOrder(reserveDBId)
        val threadBookmarkGroupToCreate = createTransaction.toCreate[groupId]!!

        threadBookmarkGroupToCreate.entries.add(
          ThreadBookmarkGroupEntryToCreate(
            ownerGroupId = groupId,
            threadDescriptor = threadDescriptor,
            orderInGroup = newBookmarkOrder
          )
        )
      }

      if (createTransaction.isEmpty()) {
        return@withLockNonCancellable true
      }

      return@withLockNonCancellable createNewGroupsInternal(createTransaction)
    }
  }

  suspend fun createNewGroupEntriesFromFilterWatcher(bookmarkGroupsToCreate: List<SimpleThreadBookmarkGroupToCreate>) {
    if (bookmarkGroupsToCreate.isEmpty()) {
      return
    }

    ensureInitialized()

    mutex.withLockNonCancellable {
      val createTransaction = CreateBookmarkGroupEntriesTransaction()

      bookmarkGroupsToCreate.forEach { bookmarkGroupToCreate ->
        var groupName = bookmarkGroupToCreate.groupName
        var groupId = ThreadBookmarkGroup.rawGroupNameToGroupId(groupName)

        if (groupId == null) {
          groupId = ThreadBookmarkGroup.DEFAULT_GROUP_ID
          groupName = ThreadBookmarkGroup.DEFAULT_GROUP_NAME
        }

        val groupOrder = if (groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId]!!.groupOrder
        } else {
          getNextGroupOrder()
        }

        if (!groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId] = ThreadBookmarkGroup(
            groupId = groupId,
            groupName = groupName,
            isExpanded = true,
            groupOrder = groupOrder,
            newMatchingPattern = bookmarkGroupToCreate.matchingPattern
          )
        }

        val threadBookmarkGroup = groupsByGroupIdMap[groupId]!!
        val reserveDBId = ThreadBookmarkGroup.nextReserveDBId()

        val entries = bookmarkGroupToCreate.entries.mapNotNull { threadDescriptor ->
          val containsBookmarkEntry = groupsByGroupIdMap
            .values
            .any { threadBookmarkGroup -> threadBookmarkGroup.contains(threadDescriptor) }

          if (containsBookmarkEntry) {
            return@mapNotNull null
          }

          return@mapNotNull ThreadBookmarkGroupEntryToCreate(
            ownerGroupId = groupId,
            threadDescriptor = threadDescriptor,
            orderInGroup = threadBookmarkGroup.reserveSpaceForBookmarkOrder(reserveDBId)
          )
        }

        if (entries.isEmpty()) {
          return@forEach
        }

        createTransaction.toCreate[groupId] = ThreadBookmarkGroupToCreate(
          reserveDBId = reserveDBId,
          groupId = groupId,
          groupName = groupName,
          isExpanded = true,
          groupOrder = groupOrder,
          matchingPattern = bookmarkGroupToCreate.matchingPattern,
          entries = entries.toMutableList()
        )

        if (createTransaction.isEmpty()) {
          return@forEach
        }
      }

      createNewGroupsInternal(createTransaction)
    }
  }

  private suspend fun createNewGroupsInternal(
    createTransaction: CreateBookmarkGroupEntriesTransaction
  ): Boolean {
    require(mutex.isLocked) { "Mutex is not locked!" }
    require(!createTransaction.isEmpty()) { "createTransaction is empty!" }

    // Try to insert everything into the database
    threadBookmarkGroupRepository.executeCreateTransaction(createTransaction)
      .safeUnwrap { error ->
        Logger.e(TAG, "Error trying to insert new bookmark group entries into the database", error)

        createTransaction.toCreate.entries.forEach { (groupId, threadBookmarkGroupToCreate) ->
          groupsByGroupIdMap[groupId]?.removeTemporaryOrders(threadBookmarkGroupToCreate.reserveDBId)
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
          newEntries = threadBookmarkGroupEntries,
          newOrders = threadBookmarkGroupToCreate.getEntryDatabaseIdsSorted().toMutableList(),
          newMatchingPattern = threadBookmarkGroupToCreate.matchingPattern
        )
      } else {
        threadBookmarkGroupEntries.values.forEach { threadBookmarkGroupEntry ->
          val order = requireNotNull(orders[threadBookmarkGroupEntry.databaseId]) {
            "order not found by databaseId=${threadBookmarkGroupEntry.databaseId}"
          }

          groupsByGroupIdMap[groupId]?.addThreadBookmarkGroupEntry(
            reserveDBId = threadBookmarkGroupToCreate.reserveDBId,
            threadBookmarkGroupEntry = threadBookmarkGroupEntry,
            orderInGroup = order
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
    return mutex.withLockNonCancellable {
      val grouped = mutableMapOf<String, MutableList<ThreadBookmarkGroupEntry>>()
      val deleteTransaction = DeleteBookmarkGroupEntriesTransaction()

      // 1. Find ThreadBookmarkGroupEntry that we want to delete by their ThreadDescriptors
      for (bookmarkThreadDescriptor in bookmarkThreadDescriptors) {
        for ((groupId, threadBookmarkGroup) in groupsByGroupIdMap) {
          if (!threadBookmarkGroup.contains(bookmarkThreadDescriptor)) {
            continue
          }

          val groupEntry = threadBookmarkGroup.getGroupEntryByThreadDescriptor(bookmarkThreadDescriptor)
            ?: continue

          grouped.getOrPut(
            key = groupId,
            defaultValue = { mutableListOf() }
          ).also { groupList -> groupList.add(groupEntry) }
        }
      }

      // 2. Remove the from the groupsByGroupIdMap + fill in the deleteTransaction
      grouped.forEach { (groupId, threadBookmarkGroupEntryList) ->
        threadBookmarkGroupEntryList.forEach { threadBookmarkGroupEntry ->
          groupsByGroupIdMap[groupId]
            ?.removeThreadBookmarkGroupEntry(threadBookmarkGroupEntry)
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
        return@withLockNonCancellable true
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
              groupsByGroupIdMap[groupId]
                ?.addThreadBookmarkGroupEntry(threadBookmarkGroupEntry)
            }

            groupsByGroupIdMap[groupId]?.checkConsistency()
          }

          return@withLockNonCancellable false
        }

      return@withLockNonCancellable true
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

        createGroupEntries(bookmarkThreadDescriptors = threadDescriptors, forceDefaultGroup = false)
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
            mutex.withLockNonCancellable {
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

    if (!groupsByGroupIdMap.containsKey(ThreadBookmarkGroup.DEFAULT_GROUP_ID)) {
      createTransaction.toCreate[ThreadBookmarkGroup.DEFAULT_GROUP_ID] = ThreadBookmarkGroupToCreate(
        reserveDBId = -1,
        groupId = ThreadBookmarkGroup.DEFAULT_GROUP_ID,
        groupName = ThreadBookmarkGroup.DEFAULT_GROUP_NAME,
        isExpanded = true,
        groupOrder = maxOrder++,
        entries = mutableListOf(),
        matchingPattern = null
      )
    }

    if (createTransaction.isEmpty()) {
      return
    }

    threadBookmarkGroupRepository.executeCreateTransaction(createTransaction)
      .onError { error -> Logger.e(TAG, "Error trying to create new bookmark group", error) }
      .unwrap()

    createTransaction.toCreate.entries.forEach { (groupId, threadBookmarkGroupToCreate) ->
      val defaultGroup = ThreadBookmarkGroup(
        groupId = groupId,
        groupName = threadBookmarkGroupToCreate.groupName,
        isExpanded = threadBookmarkGroupToCreate.isExpanded,
        groupOrder = threadBookmarkGroupToCreate.groupOrder,
        newMatchingPattern = threadBookmarkGroupToCreate.matchingPattern
      )

      groupsByGroupIdMap[groupId] = defaultGroup
    }
  }

  private fun getGroupIdSetByThreadDescriptor(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): Set<String> {
    require(mutex.isLocked) { "Mutex is not locked!" }
    val resultSet = hashSetWithCap<String>(threadDescriptors.size)

    fun findGroupOrUseDefaultGroup(threadDescriptor: ChanDescriptor.ThreadDescriptor): String {
      for (threadBookmarkGroup in groupsByGroupIdMap.values) {
        if (threadBookmarkGroup.contains(threadDescriptor)) {
          return threadBookmarkGroup.groupId
        }
      }

      return ThreadBookmarkGroup.DEFAULT_GROUP_ID
    }

    for (threadDescriptor in threadDescriptors) {
      resultSet.add(findGroupOrUseDefaultGroup(threadDescriptor))
    }

    return resultSet
  }

  data class GroupIdWithName(val groupId: String, val groupName: String)

  class GroupCreationError(message: String) : Exception(message)

  companion object {
    private const val TAG = "ThreadBookmarkGroupManager"
  }

}