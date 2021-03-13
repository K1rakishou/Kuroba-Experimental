package com.github.k1rakishou.chan.core.site.parser

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.*

/**
 * This class is used to add mock replies to posts that were made by you or marked as yours. The
 * main point of this is to tests things related to replies to your posts (like notifications
 * showing up on (You)s and such). This is only should be used for development purposes.
 *
 * If you want to add a mock reply to a post that was not made by you then you should mark that
 * post as yours beforehand (in case you want to test (You) notification show up) because this class
 * DOES NOT do that automatically for you.
 *
 * Also, the replies are not persisted across application lifecycle, so once the app dies all
 * replies in the mockReplyMultiMap will be gone and you will have to add them again.
 *
 * ThreadSafe.
 * */
@DoNotStrip
open class MockReplyManager {
  @GuardedBy("this")
  private val mockReplyMultiMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, LinkedList<Long>>()

  fun addMockReply(siteName: String, boardCode: String, opNo: Long, postNo: Long) {
    if (opNo <= 0 || postNo <= 0) {
      return
    }

    synchronized(this) {
      val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        siteName = siteName,
        boardCode = boardCode,
        threadNo = opNo
      )

      if (!mockReplyMultiMap.containsKey(threadDescriptor)) {
        mockReplyMultiMap[threadDescriptor] = LinkedList()
      }

      mockReplyMultiMap[threadDescriptor]!!.addFirst(postNo)
      Logger.d(TAG, "addMockReply() mock replies count = ${mockReplyMultiMap.size}")
    }
  }

  fun getLastMockReply(siteName: String, boardCode: String, opNo: Long): Long {
    if (opNo <= 0) {
      return -1L
    }

    return synchronized(this) {
      val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        siteName = siteName,
        boardCode = boardCode,
        threadNo = opNo
      )

      val repliesQueue = mockReplyMultiMap[threadDescriptor]
        ?: return@synchronized -1

      if (repliesQueue.isEmpty()) {
        mockReplyMultiMap.remove(threadDescriptor)
        return@synchronized -1
      }

      val lastReply = repliesQueue.removeLast()
      Logger.d(TAG, "getLastMockReplyOrNull() mock replies " +
        "count = ${mockReplyMultiMap.values.sumBy { queue -> queue.size }}")

      if (repliesQueue.isEmpty()) {
        mockReplyMultiMap.remove(threadDescriptor)
      }

      return@synchronized lastReply
    }
  }

  companion object {
    private const val TAG = "MockReplyManager"
  }
}