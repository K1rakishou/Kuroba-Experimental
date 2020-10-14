package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.features.bookmarks.data.GroupOfThreadBookmarkItemViews
import com.github.k1rakishou.chan.features.bookmarks.data.ThreadBookmarkItemView
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadBookmarkGroupManager(
  private val appScope: CoroutineScope,
  private val threadBookmarkGroupEntryRepository: ThreadBookmarkGroupRepository
) {
  private val lock = ReentrantReadWriteLock()
  private val suspendableInitializer = SuspendableInitializer<Unit>("ThreadBookmarkGroupManager")

  @GuardedBy("lock")
  // Map<GroupId, ThreadBookmarkGroupEntry>
  private val groupsByGroupIdMap = mutableMapOf<String, ThreadBookmarkGroup>()

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

  fun groupBookmarks(threadBookmarkViewList: List<ThreadBookmarkItemView>): List<GroupOfThreadBookmarkItemViews> {
    check(isReady()) { "ThreadBookmarkGroupEntryManager is not ready yet! Use awaitUntilInitialized()" }

    return lock.read {
      val bookmarksByGroupIdMap = threadBookmarkViewList
        .groupBy { threadBookmarkItemView -> threadBookmarkItemView.groupId }
      val bookmarksByThreadDescriptorMap = threadBookmarkViewList
        .associateBy { threadBookmarkItemView -> threadBookmarkItemView.threadDescriptor }

      val listOfGroups = mutableListWithCap<GroupOfThreadBookmarkItemViews>(bookmarksByGroupIdMap.keys.size)
      val sortedGroupIds = arrayOfNulls<String>(bookmarksByGroupIdMap.keys.size)

      bookmarksByGroupIdMap.keys.forEach { groupId ->
        val threadBookmarkGroup = groupsByGroupIdMap[groupId]
          ?: return@forEach

        sortedGroupIds[threadBookmarkGroup.order] = threadBookmarkGroup.groupId
      }

      sortedGroupIds.forEach { groupId ->
        val threadBookmarkGroup = groupsByGroupIdMap[groupId]
          ?: return@forEach

        val threadBookmarkViews = arrayOfNulls<ThreadBookmarkItemView>(threadBookmarkGroup.entries.size)

        threadBookmarkGroup.entries.forEach { (_, bookmarkGroupEntry) ->
          val orderInGroup = bookmarkGroupEntry.orderInGroup
          val threadDescriptor = bookmarkGroupEntry.threadDescriptor

          threadBookmarkViews[orderInGroup] = bookmarksByThreadDescriptorMap[threadDescriptor]
            ?: return@forEach
        }

        listOfGroups += GroupOfThreadBookmarkItemViews(
          groupId = threadBookmarkGroup.groupId,
          groupName = threadBookmarkGroup.groupName,
          threadBookmarkViews = threadBookmarkViews
            .map { threadBookmarkView -> threadBookmarkView!! }
            .toList()
        )
      }

      return@read listOfGroups
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

  companion object {
    private const val TAG = "ThreadBookmarkGroupEntryManager"
  }

}