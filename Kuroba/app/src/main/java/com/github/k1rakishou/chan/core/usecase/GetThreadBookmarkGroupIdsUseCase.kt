package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetThreadBookmarkGroupIdsUseCase(
  private val _threadBookmarkGroupManager: Lazy<ThreadBookmarkGroupManager>,
  private val _chanThreadManager: Lazy<ChanThreadManager>
) : ISuspendUseCase<
  GetThreadBookmarkGroupIdsUseCase.Parameters,
  GetThreadBookmarkGroupIdsUseCase.Result
  > {

  private val threadBookmarkGroupManager: ThreadBookmarkGroupManager
    get() = _threadBookmarkGroupManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()

  override suspend fun execute(parameter: Parameters): Result {
    return withContext(Dispatchers.IO) {
      val bookmarkThreadDescriptors = parameter.bookmarkThreadDescriptors
      if (bookmarkThreadDescriptors.isEmpty()) {
        return@withContext Result(emptyMap())
      }

      val resultMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, ThreadBookmarkGroupManager.GroupIdWithName>()

      bookmarkThreadDescriptors.forEach { threadDescriptor ->
        // If any of the existing groups already contains this thread descriptor then do nothing
        if (threadBookmarkGroupManager.contains(threadDescriptor)) {
          return@forEach
        }

        val chanOriginalPost = chanThreadManager.getChanThread(threadDescriptor)?.getOriginalPostSafe()
        if (chanOriginalPost == null) {
          resultMap[threadDescriptor] = defaultGroup()
          return@forEach
        }

        val groupIdWithName = threadBookmarkGroupManager.getMatchingGroupIdWithName(
          chanOriginalPost.postDescriptor.threadDescriptor().boardDescriptor,
          chanOriginalPost.subject ?: "",
          chanOriginalPost.postComment.comment()
        )

        resultMap[threadDescriptor] = groupIdWithName
      }

      return@withContext Result(resultMap)
    }
  }

  private fun defaultGroup(): ThreadBookmarkGroupManager.GroupIdWithName {
    return ThreadBookmarkGroupManager.GroupIdWithName(
      groupId = ThreadBookmarkGroup.DEFAULT_GROUP_ID,
      groupName = ThreadBookmarkGroup.DEFAULT_GROUP_NAME
    )
  }

  data class Parameters(val bookmarkThreadDescriptors: List<ChanDescriptor.ThreadDescriptor>)
  data class Result(val groupsMap: Map<ChanDescriptor.ThreadDescriptor, ThreadBookmarkGroupManager.GroupIdWithName>)
}