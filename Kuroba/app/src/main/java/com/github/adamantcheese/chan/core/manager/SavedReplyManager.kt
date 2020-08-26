package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.putIfNotContains
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanSavedReply
import com.github.adamantcheese.model.repository.ChanSavedReplyRepository
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class SavedReplyManager(
  private val verboseLogsEnabled: Boolean,
  private val savedReplyRepository: ChanSavedReplyRepository
) {
  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val savedReplyMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableList<ChanSavedReply>>()

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) begin")
    }

    val savedReplies = savedReplyRepository.preloadForThread(threadDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "Error while trying to preload saved replies for thread ($threadDescriptor)", error)
        return
      }

    lock.write { savedReplyMap[threadDescriptor] = savedReplies.toMutableList() }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadForThread($threadDescriptor) end")
    }
  }

  fun isSaved(chanDescriptor: ChanDescriptor, postNo: Long, postSubNo: Long): Boolean {
    val threadDescriptor = when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> chanDescriptor
      is ChanDescriptor.CatalogDescriptor -> ChanDescriptor.ThreadDescriptor.create(chanDescriptor, postNo)
    }

    return lock.read {
      return@read savedReplyMap[threadDescriptor]?.any { chanSavedReply ->
        chanSavedReply.postDescriptor.postNo == postNo
          && chanSavedReply.postDescriptor.postSubNo == postSubNo
      } ?: false
    }
  }

  fun isSaved(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      return@read savedReplyMap[postDescriptor.threadDescriptor()]?.any { chanSavedReply ->
        chanSavedReply.postDescriptor == postDescriptor
      } ?: false
    }
  }

  fun getSavedReply(postDescriptor: PostDescriptor): ChanSavedReply? {
    return lock.read {
      return@read savedReplyMap[postDescriptor.threadDescriptor()]?.firstOrNull { chanSavedReply ->
        chanSavedReply.postDescriptor == postDescriptor
      }
    }
  }

  suspend fun unsavePost(postDescriptor: PostDescriptor) {
    savedReplyRepository.unsavePost(postDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "unsavePost($postDescriptor) error", error)
        return
      }

    lock.write {
      val index = savedReplyMap[postDescriptor.threadDescriptor()]
        ?.indexOfFirst { chanSavedReply -> chanSavedReply.postDescriptor == postDescriptor } ?: -1

      if (index >= 0) {
        savedReplyMap[postDescriptor.threadDescriptor()]?.removeAt(index)
      }
    }
  }

  suspend fun savePost(postDescriptor: PostDescriptor) {
    val savedReply = ChanSavedReply(postDescriptor)
    saveReply(savedReply)
  }

  suspend fun saveReply(savedReply: ChanSavedReply) {
    val postDescriptor = savedReply.postDescriptor

    savedReplyRepository.savePost(savedReply)
      .safeUnwrap { error ->
        Logger.e(TAG, "savePost($postDescriptor) error", error)
        return
      }

    lock.write {
      val index = savedReplyMap[postDescriptor.threadDescriptor()]
        ?.indexOfFirst { chanSavedReply -> chanSavedReply.postDescriptor == postDescriptor } ?: -1

      if (index < 0) {
        savedReplyMap[postDescriptor.threadDescriptor()]?.add(savedReply)
      }
    }
  }

  fun retainSavedPostNoMap(
    quoteOwnerPostsMap: Map<Long, Set<Long>>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): Map<Long, MutableSet<Long>> {
    val resultMap: MutableMap<Long, MutableSet<Long>> = HashMap(16)

    lock.read {
      val savedRepliesNoSet = savedReplyMap[threadDescriptor]
        ?.map { chanSavedReply -> chanSavedReply.postDescriptor.postNo }
        ?.toSet()
        ?: return@read

      for ((quotePostNo, quotedPostNos) in quoteOwnerPostsMap) {
        for (quotedPostNo in quotedPostNos) {
          if (!savedRepliesNoSet.contains(quotePostNo)) {
            continue
          }

          resultMap.putIfNotContains(quotePostNo, mutableSetOf())
          resultMap[quotePostNo]!!.add(quotedPostNo)
        }
      }
    }

    return resultMap
  }

  fun retainSavedPostNoMap(
    postList: List<Post>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): List<Long> {
    if (postList.isEmpty()) {
      return emptyList()
    }

    return lock.read {
      val savedRepliesNoSet = savedReplyMap[threadDescriptor]
        ?.map { chanSavedReply -> chanSavedReply.postDescriptor.postNo }
        ?.toSet()
        ?: return@read emptyList()

      return@read postList
        .filter { post -> savedRepliesNoSet.contains(post.no) }
        .map { post -> post.no }
    }
  }

  companion object {
    private const val TAG = "SavedReplyManager"
  }
}