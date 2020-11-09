package com.github.k1rakishou.model.data.thread

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ChanThread(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor
) {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val threadPosts = mutableListOf<ChanPost>()
  @GuardedBy("lock")
  private val postsByPostDescriptors = mutableMapOf<PostDescriptor, ChanPost>()
  @GuardedBy("lock")
  private var lastAccessTime = System.currentTimeMillis()

  val postsCount: Int
    get() = lock.read { threadPosts.size }

  val repliesCount: Int
    get() = lock.read {
      val postsTotal = postsCount
      if (postsTotal <= 0) {
        return@read 0
      }

      return@read postsTotal - 1
    }

  val imagesCount: Int
    get() = lock.read { threadPosts.sumBy { post -> post.postImages.size } }

  fun isClosed(): Boolean = lock.read { getOriginalPost().closed }
  fun isArchived(): Boolean = lock.read { getOriginalPost().archived }
  fun isDeleted(): Boolean = lock.read { getOriginalPost().deleted }

  fun replacePosts(newPosts: List<ChanPost>) {
    lock.write {
      require(newPosts.isNotEmpty()) { "newPosts are empty!" }
      require(newPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${newPosts.first()}"
      }

      updateLastAccessTime()

      threadPosts.clear()
      postsByPostDescriptors.clear()

      threadPosts.addAll(newPosts)
      newPosts.forEach { post -> postsByPostDescriptors[post.postDescriptor] = post }
    }
  }

  fun getOriginalPost(): ChanOriginalPost {
    lock.read {
      require(threadPosts.isNotEmpty()) { "posts are empty!" }
      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      updateLastAccessTime()
      return threadPosts.first() as ChanOriginalPost
    }
  }

  fun getPostDescriptors(): List<PostDescriptor> {
    return lock.read {
      updateLastAccessTime()
      return@read threadPosts.map { chanPost -> chanPost.postDescriptor }
    }
  }

  private fun updateLastAccessTime() {
    lock.write { lastAccessTime = System.currentTimeMillis() }
  }

  fun getLastAccessTime(): Long {
    return lock.read { lastAccessTime }
  }

  fun setOrUpdateOriginalPost(chanOriginalPost: ChanOriginalPost) {
    lock.write {
      updateLastAccessTime()

      val oldPostDescriptor = threadPosts.firstOrNull()?.postDescriptor
      val newPostDescriptor = chanOriginalPost.postDescriptor

      if (oldPostDescriptor != null) {
        check(oldPostDescriptor == newPostDescriptor) {
          "Post descriptors are not the same! (old: $oldPostDescriptor, new: $newPostDescriptor)"
        }
      }

      if (threadPosts.isNotEmpty()) {
        require(threadPosts.first() is ChanOriginalPost) {
          "First post is not an original post! post=${threadPosts.first()}"
        }

        threadPosts[0] = chanOriginalPost
      } else {
        threadPosts.add(chanOriginalPost)
      }

      postsByPostDescriptors[chanOriginalPost.postDescriptor] = chanOriginalPost
    }
  }

  fun setDeleted(deleted: Boolean) {
    lock.write {
      require(threadPosts.isNotEmpty()) { "posts are empty!" }
      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      updateLastAccessTime()
      threadPosts[0].setPostDeleted(deleted)
    }
  }

  fun cleanupPostsInRollingStickyThread(threadCap: Int): List<PostDescriptor> {
    if (threadCap <= 0) {
      return emptyList()
    }

    return lock.write {
      if (threadPosts.size <= threadCap) {
        return@write emptyList()
      }

      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      val toDeleteCount = threadPosts.size - threadCap
      if (toDeleteCount <= 0) {
        return@write emptyList()
      }

      val postsToDelete = threadPosts.subList(1, toDeleteCount)
      threadPosts.removeAll(postsToDelete)

      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      return@write postsToDelete.map { chanPost -> chanPost.postDescriptor }
    }
  }

  fun canUpdateThread(): Boolean {
    return lock.read {
      val originalPost = getOriginalPost()

      return@read !originalPost.closed && !originalPost.deleted && !originalPost.archived
    }
  }

  fun lastPost(): ChanPost? {
    return lock.read { threadPosts.lastOrNull() }
  }

  fun getPost(postDescriptor: PostDescriptor): ChanPost? {
    return lock.read {
      updateLastAccessTime()
      return@read postsByPostDescriptors[postDescriptor]
    }
  }

  fun getNewPostsCount(lastPostNo: Long): Int {
    return lock.read { threadPosts.count { chanPost -> chanPost.postNo() > lastPostNo } }
  }

  fun firstPostOrderedOrNull(predicate: (ChanPost) -> Boolean): ChanPost? {
    return lock.read {
      if (threadPosts.isEmpty()) {
        return@read null
      }

      updateLastAccessTime()

      for (index in threadPosts.indices) {
        val chanPost = threadPosts.getOrNull(index)
          ?: break

        if (predicate(chanPost)) {
          return@read chanPost
        }
      }

      return@read null
    }
  }

  fun findPostWithRepliesRecursive(
    postNo: Long,
    postsSet: MutableSet<ChanPost>
  ) {
    lock.read {
      for (post in threadPosts) {
        if (post.postNo() == postNo && !postsSet.contains(post)) {
          postsSet.add(post)

          post.iterateRepliesFrom { replyId ->
            findPostWithRepliesRecursive(replyId, postsSet)
          }
        }
      }
    }
  }

  fun deletePost(postDescriptor: PostDescriptor) {
    lock.write {
      require(threadPosts.isNotEmpty()) { "posts are empty!" }
      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      updateLastAccessTime()

      val postIndex = threadPosts.indexOfFirst { chanPost ->
        chanPost.postDescriptor == postDescriptor
      }

      if (postIndex >= 0) {
        threadPosts.removeAt(postIndex)
      }

      postsByPostDescriptors.remove(postDescriptor)
    }
  }

  fun iteratePostsOrdered(iterator: (ChanPost) -> Unit) {
    lock.read {
      if (threadPosts.isEmpty()) {
        return@read
      }

      updateLastAccessTime()

      for (index in threadPosts.indices) {
        val chanPost = threadPosts.getOrNull(index)
          ?: return@read

        iterator(chanPost)
      }
    }
  }

  fun mapPostsWithImagesAround(
    postDescriptor: PostDescriptor,
    leftCount: Int,
    rightCount: Int
  ): List<PostDescriptor> {
    if (leftCount == 0 && rightCount == 0) {
      return emptyList()
    }

    check(leftCount >= 0) { "Bad left count: $leftCount" }
    check(rightCount >= 0) { "Bad right count: $rightCount" }

    return lock.read {
      val indexOfPost = threadPosts.indexOfFirst { post -> post.postDescriptor == postDescriptor }
      if (indexOfPost < 0) {
        return@read emptyList()
      }

      val totalCount = leftCount + rightCount
      val postDescriptors = mutableListWithCap<PostDescriptor>(totalCount)

      // Check current post and add it to the list if it has images
      threadPosts.getOrNull(indexOfPost)?.let { currentPost ->
        if (currentPost.postImages.isNotEmpty()) {
          postDescriptors += currentPost.postDescriptor
        }
      }

      var currentPostIndex = indexOfPost - 1
      var takeFromLeft = leftCount

      // Check posts to the left of the current post and add to the list those that have images
      while (takeFromLeft > 0 && currentPostIndex in threadPosts.indices) {
        val post = threadPosts.getOrNull(currentPostIndex--)
          ?: break

        if (post.postImages.isEmpty()) {
          continue
        }

        --takeFromLeft
        postDescriptors += post.postDescriptor
      }

      currentPostIndex = indexOfPost + 1
      var takeFromRight = rightCount

      // Check posts to the right of the current post and add to the list those that have images
      while (takeFromRight > 0 && currentPostIndex in threadPosts.indices) {
        val post = threadPosts.getOrNull(currentPostIndex++)
          ?: break

        if (post.postImages.isEmpty()) {
          continue
        }

        --takeFromRight
        postDescriptors += post.postDescriptor
      }

      return@read postDescriptors
    }
  }

  fun getPostDescriptorRelativeTo(postDescriptor: PostDescriptor, offset: Int): PostDescriptor? {
    return lock.read {
      val currentPostIndex = threadPosts.indexOfFirst { post -> post.postDescriptor == postDescriptor }
      if (currentPostIndex < 0) {
        return@read null
      }

      val postIndex = (currentPostIndex + offset).coerceIn(0, threadPosts.size)
      return@read threadPosts.getOrNull(postIndex)?.postDescriptor
    }
  }

  fun iteratePostImages(postDescriptor: PostDescriptor, iterator: (ChanPostImage) -> Unit): Boolean {
    return lock.read {
      val post = postsByPostDescriptors[postDescriptor]
        ?: return@read false

      post.iteratePostImages { postImage -> iterator(postImage) }
      return@read true
    }
  }

  fun postHasImages(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      return@read postsByPostDescriptors[postDescriptor]?.postImages?.isNotEmpty()
        ?: false
    }
  }

  fun hasAtLeastOnePost(): Boolean {
    return lock.read { threadPosts.isNotEmpty() }
  }
}