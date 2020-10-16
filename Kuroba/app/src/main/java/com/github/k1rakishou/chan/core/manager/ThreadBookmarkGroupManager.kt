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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadBookmarkGroupManager(
  private val appScope: CoroutineScope,
  private val verboseLogs: Boolean,
  private val threadBookmarkGroupEntryRepository: ThreadBookmarkGroupRepository,
  private val bookmarksManager: BookmarksManager
) {
  private val lock = ReentrantReadWriteLock()
  private val suspendableInitializer = SuspendableInitializer<Unit>("ThreadBookmarkGroupManager")

  @GuardedBy("lock")
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
          lock.write {
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

    return lock.read {
      val bookmarksByGroupIdMap = threadBookmarkViewList
        .groupBy { threadBookmarkItemView -> threadBookmarkItemView.groupId }
      val bookmarksByThreadDescriptorMap = threadBookmarkViewList
        .associateBy { threadBookmarkItemView -> threadBookmarkItemView.threadDescriptor }

      val listOfGroups =
        mutableListWithCap<GroupOfThreadBookmarkItemViews>(bookmarksByGroupIdMap.keys.size)

      // TODO(KurobaEx): this may crash
      val sortedGroupIds = arrayOfNulls<String>(bookmarksByGroupIdMap.keys.size)

      bookmarksByGroupIdMap.keys.forEach { groupId ->
        val threadBookmarkGroup = groupsByGroupIdMap[groupId]
          ?: return@forEach

        sortedGroupIds[threadBookmarkGroup.order] = threadBookmarkGroup.groupId
      }

      sortedGroupIds.forEach { groupId ->
        val threadBookmarkGroup = groupsByGroupIdMap[groupId]
          ?: return@forEach

        // TODO(KurobaEx): this may crash
        val threadBookmarkViews = arrayOfNulls<ThreadBookmarkItemView>(threadBookmarkGroup.entries.size)

        threadBookmarkGroup.entries.forEach { (_, bookmarkGroupEntry) ->
          val orderInGroup = bookmarkGroupEntry.orderInGroup
          val threadDescriptor = bookmarkGroupEntry.threadDescriptor

          val tbView = bookmarksByThreadDescriptorMap[threadDescriptor]
          if (tbView == null) {
            Logger.e(TAG, "bookmarksByThreadDescriptorMap does not contain " +
              "threadBookmarkView with descriptor: ${threadDescriptor}")
            return@forEach
          }

          threadBookmarkViews[orderInGroup] = tbView
        }

        val resultThreadBookmarkViews = threadBookmarkViews
          .mapNotNull { threadBookmarkView -> threadBookmarkView }
          .toList()

        listOfGroups += GroupOfThreadBookmarkItemViews(
          groupId = threadBookmarkGroup.groupId,
          groupInfoText = threadBookmarkGroup.groupName,
          isExpanded = threadBookmarkGroup.isExpanded,
          threadBookmarkViews = resultThreadBookmarkViews
        )
      }

      return@read listOfGroups
    }
  }

  suspend fun toggleBookmarkExpandState(groupId: String): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }

    return lock.write {
      val group = groupsByGroupIdMap[groupId]
        ?: return@write false

      val newIsExpanded = !group.isExpanded
      group.isExpanded = newIsExpanded

      threadBookmarkGroupEntryRepository.updateBookmarkGroupExpanded(groupId, newIsExpanded)
        .safeUnwrap { error ->
          groupsByGroupIdMap[groupId]?.isExpanded = !newIsExpanded

          Logger.e(TAG, "updateBookmarkGroupExpanded error", error)
          return@write false
        }

      return@write true
    }
  }

  suspend fun createGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    // Yes, we are locking the database access here, because we need to calculate the new order for
    // groups and and regular entries and we don't want anyone modifying groupsByGroupIdMap while
    // we are persisting the changes.
    return lock.write {
      val createTransaction = CreateBookmarkGroupEntriesTransaction()

      bookmarksManager.viewBookmarks(bookmarkThreadDescriptors) { threadBookmarkView ->
        val groupId = threadBookmarkView.groupId

        val groupExists = groupsByGroupIdMap.containsKey(groupId)
          || createTransaction.toCreate.containsKey(groupId)

        if (!groupExists) {
          val newGroupOrder = groupsByGroupIdMap.values
            .maxOfOrNull { threadBookmarkGroup -> threadBookmarkGroup.order }
            ?.plus(1) ?: 0

          createTransaction.toCreate.put(
            groupId,
            ThreadBookmarkGroupToCreate(
              groupId = groupId,
              groupName = groupId,
              isExpanded = false,
              order = newGroupOrder,
              entries = mutableListOf()
            )
          )
        }

        val containsBookmarkEntry = groupsByGroupIdMap[groupId]
          ?.entries
          ?.values
          ?.any { threadBookmarkGroupEntry ->
            return@any threadBookmarkGroupEntry.threadDescriptor == threadBookmarkView.threadDescriptor
          } ?: false

        if (containsBookmarkEntry) {
          return@viewBookmarks
        }

        val newBookmarkOrder = groupsByGroupIdMap[groupId]?.entries?.values
          ?.maxOfOrNull { threadBookmarkGroupEntry -> threadBookmarkGroupEntry.orderInGroup }
          ?.plus(1) ?: 0

        createTransaction.toCreate[groupId]!!.entries.add(
          ThreadBookmarkGroupEntryToCreate(
            ownerGroupId = groupId,
            threadDescriptor = threadBookmarkView.threadDescriptor,
            orderInGroup = newBookmarkOrder
          )
        )
      }

      if (createTransaction.isEmpty()) {
        return@write true
      }

      threadBookmarkGroupEntryRepository.executeCreateTransaction(createTransaction)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error trying to insert new bookmark group entries into the database", error)

          // Nothing to rollback, just exit
          return@write false
        }

      createTransaction.toCreate.forEach { (groupId, threadBookmarkGroupToCreate) ->
        val threadBookmarkGroupEntries = ConcurrentHashMap<Long, ThreadBookmarkGroupEntry>()

        threadBookmarkGroupToCreate.entries.forEach { threadBookmarkGroupEntryToCreate ->
          val databaseId = threadBookmarkGroupEntryToCreate.databaseId
          check(databaseId > 0L) { "Bad databaseId: $databaseId" }

          val ownerBookmarkId = threadBookmarkGroupEntryToCreate.ownerBookmarkId
          check(ownerBookmarkId > 0L) { "Bad ownerBookmarkId: $ownerBookmarkId" }

          threadBookmarkGroupEntries[databaseId] = ThreadBookmarkGroupEntry(
            databaseId = databaseId,
            ownerGroupId = groupId,
            ownerBookmarkId = ownerBookmarkId,
            threadDescriptor = threadBookmarkGroupEntryToCreate.threadDescriptor,
            orderInGroup = threadBookmarkGroupEntryToCreate.orderInGroup
          )
        }

        if (!groupsByGroupIdMap.containsKey(groupId)) {
          groupsByGroupIdMap[groupId] = ThreadBookmarkGroup(
            groupId,
            threadBookmarkGroupToCreate.groupName,
            threadBookmarkGroupToCreate.isExpanded,
            threadBookmarkGroupToCreate.order,
            threadBookmarkGroupEntries
          )

          return@forEach
        }

        threadBookmarkGroupEntries.values.forEach { threadBookmarkGroupEntry ->
          groupsByGroupIdMap[groupId]?.entries?.put(
            threadBookmarkGroupEntry.databaseId,
            threadBookmarkGroupEntry
          )
        }
      }

      return@write true
    }
  }

  suspend fun deleteGroupEntries(bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>): Boolean {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }
    require(bookmarkThreadDescriptors.isNotEmpty()) { "bookmarkThreadDescriptors is empty!" }

    // Yes, we are locking the database access here, because we want this method to be atomic
    return lock.write {
      val grouped = mutableMapOf<String, MutableList<ThreadBookmarkGroupEntry>>()
      val deleteTransaction = DeleteBookmarkGroupEntriesTransaction()

      for (bookmarkThreadDescriptor in bookmarkThreadDescriptors) {
        outer@ for ((groupId, threadBookmarkGroup) in groupsByGroupIdMap) {
          for ((_, threadBookmarkGroupEntry) in threadBookmarkGroup.entries) {
            if (threadBookmarkGroupEntry.threadDescriptor == bookmarkThreadDescriptor) {
              grouped.putIfNotContains(groupId, mutableListOf())
              grouped[groupId]!!.add(threadBookmarkGroupEntry)

              break@outer
            }
          }
        }
      }

      grouped.forEach { (groupId, threadBookmarkGroupEntryList) ->
        threadBookmarkGroupEntryList.forEach { threadBookmarkGroupEntry ->
          groupsByGroupIdMap[groupId]?.entries?.remove(threadBookmarkGroupEntry.databaseId)
        }

        reorder(groupId)
        deleteTransaction.toDelete.addAll(threadBookmarkGroupEntryList)

        groupsByGroupIdMap[groupId]?.entries?.values?.let { threadBookmarkGroupEntries ->
          deleteTransaction.toUpdate.put(groupId, threadBookmarkGroupEntries.toList())
        }
      }

      if (deleteTransaction.isEmpty()) {
        return@write true
      }

      threadBookmarkGroupEntryRepository.executeDeleteTransaction(deleteTransaction)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error trying to delete bookmark group entries from the database", error)

          // Rollback the changes we did
          grouped.forEach { (groupId, threadBookmarkGroupEntryList) ->
            threadBookmarkGroupEntryList.forEach { threadBookmarkGroupEntry ->
              groupsByGroupIdMap[groupId]?.entries?.put(
                threadBookmarkGroupEntry.databaseId,
                threadBookmarkGroupEntry
              )
            }

            reorder(groupId)
          }

          return@write false
        }

      return@write true
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

  private fun reorder(groupId: String) {
    require(lock.isWriteLocked) { "lock is not write locked!" }

    groupsByGroupIdMap[groupId]?.entries?.values?.forEachIndexed { index, threadBookmarkGroupEntry ->
      threadBookmarkGroupEntry.orderInGroup = index
    }
  }

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