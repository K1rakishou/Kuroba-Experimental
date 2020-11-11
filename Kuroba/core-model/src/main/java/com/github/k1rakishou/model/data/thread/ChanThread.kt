package com.github.k1rakishou.model.data.thread

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.LoaderType
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ChanThread(
  private val isDevBuild: Boolean,
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

  fun addOrUpdatePosts(newChanPosts: List<ChanPost>): Boolean {
    return lock.write {
      require(newChanPosts.isNotEmpty()) { "newPosts are empty!" }
      require(newChanPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${newChanPosts.first()}"
      }

      var addedOrUpdatedPosts = false

      newChanPosts.forEach { newChanPost ->
        // We don't have this post, just add it at the end
        if (!postsByPostDescriptors.containsKey(newChanPost.postDescriptor)) {
          threadPosts.add(newChanPost)
          postsByPostDescriptors[newChanPost.postDescriptor] = newChanPost

          addedOrUpdatedPosts = true
          return@forEach
        }

        val oldChanPostIndex = threadPosts
          .indexOfFirst { post -> post.postDescriptor == newChanPost.postDescriptor }
        check(oldChanPostIndex >= 0) { "Bad oldChanPostIndex: $oldChanPostIndex" }

        val oldChanPost = threadPosts[oldChanPostIndex]
        if (oldChanPost == newChanPost) {
          return@forEach
        }

        // We already have this post, we need to merge old and new posts into one and replace old
        // post with the merged post
        val mergedPost = mergePosts(oldChanPost, newChanPost)
        threadPosts[oldChanPostIndex] = mergedPost
        postsByPostDescriptors[newChanPost.postDescriptor] = mergedPost

        addedOrUpdatedPosts = true
      }

      updateLastAccessTime()
      checkPostsConsistency()

      return@write addedOrUpdatedPosts
    }
  }

  fun setOrUpdateOriginalPost(newChanOriginalPost: ChanOriginalPost) {
    lock.write {
      val oldPostDescriptor = threadPosts.firstOrNull()?.postDescriptor
      val newPostDescriptor = newChanOriginalPost.postDescriptor

      if (oldPostDescriptor != null) {
        check(oldPostDescriptor == newPostDescriptor) {
          "Post descriptors are not the same! (old: $oldPostDescriptor, new: $newPostDescriptor)"
        }
      }

      if (threadPosts.isNotEmpty()) {
        require(threadPosts.first() is ChanOriginalPost) {
          "First post is not an original post! post=${threadPosts.first()}"
        }

        val oldChanOriginalPost = threadPosts.first()
        val mergedChanOriginalPost = mergePosts(oldChanOriginalPost, newChanOriginalPost)

        threadPosts[0] = mergedChanOriginalPost
        postsByPostDescriptors[newChanOriginalPost.postDescriptor] = mergedChanOriginalPost
      } else {
        threadPosts.add(newChanOriginalPost)
        postsByPostDescriptors[newChanOriginalPost.postDescriptor] = newChanOriginalPost
      }

      updateLastAccessTime()
      checkPostsConsistency()
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

  private fun mergePosts(oldChanPost: ChanPost, newPost: ChanPost): ChanPost {
    if (oldChanPost is ChanOriginalPost || newPost is ChanOriginalPost) {
      return mergeOriginalPosts(oldChanPost, newPost)
    }

    val mergedPost = ChanPost(
      chanPostId = oldChanPost.chanPostId,
      postDescriptor = oldChanPost.postDescriptor,
      repliesFrom = oldChanPost.repliesFrom,
      postImages = newPost.postImages,
      postIcons = newPost.postIcons,
      repliesTo = newPost.repliesTo,
      timestamp = newPost.timestamp,
      postComment = newPost.postComment,
      subject = newPost.subject,
      tripcode = newPost.tripcode,
      name = newPost.name,
      posterId = newPost.posterId,
      moderatorCapcode = newPost.moderatorCapcode,
      isSavedReply = newPost.isSavedReply
    )

    LoaderType.values().forEach { loaderType ->
      if (oldChanPost.isContentLoadedForLoader(loaderType)) {
        mergedPost.setContentLoadedForLoader(loaderType)
      }
    }

    mergedPost.setPostDeleted(oldChanPost.deleted)

    return mergedPost
  }

  private fun mergeOriginalPosts(
    oldChanPost: ChanPost,
    newPost: ChanPost
  ): ChanOriginalPost {
    require(oldChanPost is ChanOriginalPost) { "oldChanPost is not ChanOriginalPost" }
    require(newPost is ChanOriginalPost) { "newPost is not ChanOriginalPost" }

    val oldChanOriginalPost = oldChanPost as ChanOriginalPost
    val newChanOriginalPost = newPost as ChanOriginalPost

    val mergedOriginalPost = ChanOriginalPost(
      chanPostId = oldChanOriginalPost.chanPostId,
      postDescriptor = oldChanOriginalPost.postDescriptor,
      repliesFrom = oldChanOriginalPost.repliesFrom,
      postImages = newChanOriginalPost.postImages,
      postIcons = newChanOriginalPost.postIcons,
      repliesTo = newChanOriginalPost.repliesTo,
      timestamp = newChanOriginalPost.timestamp,
      postComment = newChanOriginalPost.postComment,
      subject = newChanOriginalPost.subject,
      tripcode = newChanOriginalPost.tripcode,
      name = newChanOriginalPost.name,
      posterId = newChanOriginalPost.posterId,
      moderatorCapcode = newChanOriginalPost.moderatorCapcode,
      isSavedReply = newChanOriginalPost.isSavedReply,
      catalogRepliesCount = newChanOriginalPost.catalogRepliesCount,
      catalogImagesCount = newChanOriginalPost.catalogImagesCount,
      uniqueIps = newChanOriginalPost.uniqueIps,
      lastModified = newChanOriginalPost.lastModified,
      sticky = newChanOriginalPost.sticky,
      closed = newChanOriginalPost.closed,
      archived = newChanOriginalPost.archived
    )

    LoaderType.values().forEach { loaderType ->
      if (oldChanOriginalPost.isContentLoadedForLoader(loaderType)) {
        mergedOriginalPost.setContentLoadedForLoader(loaderType)
      }
    }

    mergedOriginalPost.setPostDeleted(oldChanOriginalPost.deleted)

    return mergedOriginalPost
  }

  private fun checkPostsConsistency() {
    if (!isDevBuild) {
      return
    }

    lock.read {
      check(threadPosts.size == postsByPostDescriptors.size) {
        "Sizes do not match (threadPosts.size=${threadPosts.size}, " +
          "postsByPostDescriptors.size=${postsByPostDescriptors.size}"
      }

      var prevPostNo = Long.MIN_VALUE

      threadPosts.forEach { chanPost1 ->
        val chanPost2 = postsByPostDescriptors[chanPost1.postDescriptor]

        checkNotNull(chanPost2) { "postsByPostDescriptors does not contain $chanPost1" }
        check(chanPost1 == chanPost2) { "Posts do not match (chanPost1=$chanPost1, chanPost2=$chanPost2)" }

        check(chanPost1.postNo() > prevPostNo) { "Posts are not sorted!" }
        prevPostNo = chanPost1.postNo()
      }
    }
  }

}