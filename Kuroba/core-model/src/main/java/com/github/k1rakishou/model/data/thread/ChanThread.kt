package com.github.k1rakishou.model.data.thread

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.PostsFromServerData
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.LoaderType
import com.github.k1rakishou.model.data.post.PostComment
import com.github.k1rakishou.model.util.ChanPostUtils
import okhttp3.HttpUrl
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThread(
  private val isDevBuild: Boolean,
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  initialLastAccessTime: Long
) {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val threadPosts = mutableListOf<ChanPost>()
  @GuardedBy("lock")
  private val postsByPostDescriptors = mutableMapOf<PostDescriptor, ChanPost>()
  @GuardedBy("lock")
  // Stores hashes of unparsed post comments, the way we got the from the server, without any spans added yet.
  private val rawPostHashesMap = mutableMapOf<PostDescriptor, MurmurHashUtils.Murmur3Hash>()
  @GuardedBy("lock")
  private var lastAccessTime = initialLastAccessTime
  @GuardedBy("lock")
  private var lastUpdateTime = 0L

  // All the ***forUi variables/flags are used to display one-shot snackbar messages like
  // This thread is now sticky/no longer sticky/closed/archived/deleted etc
  @GuardedBy("lock")
  private var deletedPostsForUi = 0
  @GuardedBy("lock")
  private var isNowStickyForUi: Boolean? = null
  @GuardedBy("lock")
  private var isNowClosedForUi: Boolean? = null
  @GuardedBy("lock")
  private var isNowDeletedForUi: Boolean? = null
  @GuardedBy("lock")
  private var isNowArchivedForUi: Boolean? = null

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

  fun getAndConsumeDeletedPostsForUi(): Int {
    return lock.write {
      val deletedPosts = deletedPostsForUi
      deletedPostsForUi = 0

      return@write deletedPosts
    }
  }

  fun getAndConsumeIsStickyForUi(): Boolean? {
    return lock.write {
      val isSticky = isNowStickyForUi
      isNowStickyForUi = null

      return@write isSticky
    }
  }

  fun getAndConsumeIsClosedForUi(): Boolean? {
    return lock.write {
      val isClosed = isNowClosedForUi
      isNowClosedForUi = null

      return@write isClosed
    }
  }

  fun getAndConsumeIsDeletedForUi(): Boolean? {
    return lock.write {
      val isDeleted = isNowDeletedForUi
      isNowDeletedForUi = null

      return@write isDeleted
    }
  }

  fun getAndConsumeIsArchivedForUi(): Boolean? {
    return lock.write {
      val isArchived = isNowArchivedForUi
      isNowArchivedForUi = null

      return@write isArchived
    }
  }

  fun isClosed(): Boolean = lock.read { getOriginalPost()?.closed ?: false }
  fun isArchived(): Boolean = lock.read { getOriginalPost()?.archived ?: false }
  fun isDeleted(): Boolean = lock.read { getOriginalPost()?.isDeleted ?: false }

  fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    lock.write { rawPostHashesMap[postDescriptor] = hash }
  }

  fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    return lock.read { rawPostHashesMap[postDescriptor] }
  }

  fun clearPostHashes() {
    lock.write { rawPostHashesMap.clear() }
  }

  fun getPostImage(postDescriptor: PostDescriptor, imageUrl: HttpUrl): ChanPostImage? {
    return lock.read {
      return@read postsByPostDescriptors[postDescriptor]
        ?.firstPostImageOrNull { chanPostImage -> chanPostImage.imageUrl == imageUrl }
    }
  }

  fun getPosts(postDescriptors: Collection<PostDescriptor>): List<ChanPost> {
    return lock.read {
      val posts = mutableListWithCap<ChanPost>(postDescriptors.size)

      postDescriptors.forEach { postDescriptor ->
        posts += postsByPostDescriptors[postDescriptor]
          ?: return@forEach
      }

      return@read posts
    }
  }

  fun getAll(): List<ChanPost> {
    return lock.read { threadPosts.toList() }
  }

  @OptIn(ExperimentalTime::class)
  fun addOrUpdatePosts(newChanPosts: List<ChanPost>, postsFromServerData: PostsFromServerData?): Boolean {
    if (newChanPosts.isEmpty()) {
      return true
    }

    return lock.write {
      require(newChanPosts.isNotEmpty()) { "newPosts are empty!" }

      if (threadPosts.isNotEmpty()) {
        require(threadPosts.first() is ChanOriginalPost) {
          "First post is not an original post! post=${threadPosts.first()}"
        }
      }

      val deletedPostsSet = findDeletedPosts(threadPosts, postsFromServerData)

      var addedOrUpdatedOrDeletedPosts = false
      var addedPostsCount = 0
      var updatedPostsCount = 0
      var deletedPostsCount = 0

      newChanPosts.forEach { newChanPost ->
        require(newChanPost.postDescriptor.descriptor is ChanDescriptor.ThreadDescriptor) {
          "postDescriptor.descriptor must be thread ThreadDescriptor"
        }

        // We don't have this post, just add it at the end
        if (!postsByPostDescriptors.containsKey(newChanPost.postDescriptor)) {
          threadPosts.add(newChanPost)
          postsByPostDescriptors[newChanPost.postDescriptor] = newChanPost

          addedOrUpdatedOrDeletedPosts = true
          addedPostsCount++

          return@forEach
        }

        val oldChanPostIndex = threadPosts
          .indexOfFirst { post -> post.postDescriptor == newChanPost.postDescriptor }
        check(oldChanPostIndex >= 0) { "Bad oldChanPostIndex: $oldChanPostIndex" }

        val oldChanPost = threadPosts[oldChanPostIndex]

        if (oldChanPost is ChanOriginalPost && newChanPost is ChanOriginalPost) {
          updateThreadStatusFlagsForUi(oldChanPost, newChanPost)
        }

        // We already have this post, we need to merge old and new posts into one and replace old
        // post with the merged post
        val mergedPost = mergePosts(oldChanPost, newChanPost, deletedPostsSet)

        threadPosts[oldChanPostIndex] = mergedPost
        postsByPostDescriptors[newChanPost.postDescriptor] = mergedPost

        addedOrUpdatedOrDeletedPosts = true
        ++updatedPostsCount
      }

      if (deletedPostsSet != null && deletedPostsSet.isNotEmpty()) {
        deletedPostsSet.forEach { deletedPostDescriptor ->
          val oldChanPostIndex = threadPosts
            .indexOfFirst { post -> post.postDescriptor == deletedPostDescriptor }

          if (oldChanPostIndex < 0) {
            return@forEach
          }

          val oldPost = threadPosts.getOrNull(oldChanPostIndex)
            ?: return@forEach

          if (oldPost.isDeleted) {
            return@forEach
          }

          val updatedPost = oldPost.deepCopy(overrideDeleted = true)

          threadPosts[oldChanPostIndex] = updatedPost
          postsByPostDescriptors[updatedPost.postDescriptor] = updatedPost

          addedOrUpdatedOrDeletedPosts = true
          ++deletedPostsCount
        }
      }

      val sortAndRecalculationDuration = measureTime {
        if (addedOrUpdatedOrDeletedPosts) {
          if (!postsAreSorted()) {
            Logger.d(TAG, "addOrUpdatePosts() posts need to be sorted")
            threadPosts.sortWith(POSTS_COMPARATOR)
          }

          recalculatePostReplies(newChanPosts)
        }
      }

      deletedPostsForUi += deletedPostsCount
      checkPostsConsistency()

      Logger.d(TAG, "Thread cache (${threadDescriptor}) Added ${addedPostsCount} new posts, " +
        "updated ${updatedPostsCount} posts, marked as deleted ${deletedPostsCount} posts. " +
        "sortAndRecalculationDuration=$sortAndRecalculationDuration")

      return@write addedOrUpdatedOrDeletedPosts
    }
  }

  fun setOrUpdateOriginalPost(newChanOriginalPost: ChanOriginalPost) {
    lock.write {
      val oldPostDescriptor = threadPosts.firstOrNull()?.postDescriptor
      val newPostDescriptor = newChanOriginalPost.postDescriptor

      oldPostDescriptor?.let { oldPD ->
        check(oldPD.descriptor is ChanDescriptor.ThreadDescriptor) {
          "oldPostDescriptor.descriptor must be thread ThreadDescriptor"
        }
      }

      check(newPostDescriptor.descriptor is ChanDescriptor.ThreadDescriptor) {
        "newPostDescriptor.descriptor must be thread ThreadDescriptor"
      }

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

        if (oldChanOriginalPost is ChanOriginalPost) {
          updateThreadStatusFlagsForUi(oldChanOriginalPost, newChanOriginalPost)
        }

        val mergedChanOriginalPost = mergePosts(oldChanOriginalPost, newChanOriginalPost, null)

        threadPosts[0] = mergedChanOriginalPost
        postsByPostDescriptors[newChanOriginalPost.postDescriptor] = mergedChanOriginalPost
      } else {
        threadPosts.add(newChanOriginalPost)
        postsByPostDescriptors[newChanOriginalPost.postDescriptor] = newChanOriginalPost

        if (!postsAreSorted()) {
          Logger.d(TAG, "setOrUpdateOriginalPost() posts need to be sorted")
          threadPosts.sortWith(POSTS_COMPARATOR)
        }
      }

      checkPostsConsistency()
    }
  }

  fun getOriginalPostSafe(): ChanOriginalPost? {
    return lock.read {
      if (threadPosts.isEmpty()) {
        return@read null
      }

      val firstPost = threadPosts.first()
      if (firstPost is ChanOriginalPost) {
        return@read firstPost
      }

      return null
    }
  }

  fun getOriginalPost(): ChanOriginalPost? {
    return lock.read {
      if (threadPosts.isEmpty()) {
        return@read null
      }

      val firstPost = threadPosts.first()
      if (firstPost is ChanOriginalPost) {
        return@read firstPost
      }

      val errorMessage = buildString {
        appendLine("First post is not an original post! firstPost=${threadPosts.first()}")
        appendLine("Total posts count: ${threadPosts.size}")

        val indexOfOriginalPost = threadPosts.indexOfFirst { it is ChanOriginalPost }
        appendLine("Actual index of the original post: $indexOfOriginalPost")
      }

      error(errorMessage)
    }
  }

  fun getPostDescriptors(): List<PostDescriptor> {
    return lock.read {
      return@read threadPosts.map { chanPost -> chanPost.postDescriptor }
    }
  }

  fun updateLastAccessTime() {
    lock.write { lastAccessTime = System.currentTimeMillis() }
  }

  fun getLastAccessTime(): Long {
    return lock.read { lastAccessTime }
  }

  fun cacheNeedsUpdate(chanCacheUpdateOption: ChanCacheUpdateOptions): Boolean {
    return lock.read { chanCacheUpdateOption.canUpdate(lastUpdateTime) }
  }

  fun updateLastUpdateTime(cacheUpdateOptions: ChanCacheUpdateOptions) {
    lock.write {
      if (!cacheUpdateOptions.canUpdate(lastUpdateTime)) {
        return@write
      }

      lastUpdateTime = System.currentTimeMillis()
    }
  }

  fun updateThreadState(
    deleted: Boolean?,
    archived: Boolean?,
    closed: Boolean?
  ) {
    lock.write {
      if (threadPosts.isEmpty()) {
        return@write
      }

      val chanOriginalPost = threadPosts.first() as? ChanOriginalPost
        ?: return@write

      if (deleted != null) {
        if (chanOriginalPost.isDeleted != deleted) {
          isNowDeletedForUi = deleted
        }

        chanOriginalPost.isDeleted = deleted
      }

      if (archived != null) {
        if (chanOriginalPost.archived != archived) {
          isNowArchivedForUi = archived
        }

        chanOriginalPost.archived = archived
      }

      if (closed != null) {
        if (chanOriginalPost.closed != closed) {
          isNowClosedForUi = closed
        }

        chanOriginalPost.closed = closed
      }
    }
  }

  fun canUpdateThread(): Boolean {
    return lock.read {
      val originalPost = getOriginalPostSafe()
      if (originalPost == null) {
        // I guess we should update the thread if we have no posts?
        return@read true
      }

      return@read !originalPost.closed
        && !originalPost.isDeleted
        && !originalPost.archived
    }
  }

  fun lastPost(): ChanPost? {
    return lock.read { threadPosts.lastOrNull() }
  }

  fun getPost(postDescriptor: PostDescriptor): ChanPost? {
    return lock.read { postsByPostDescriptors[postDescriptor] }
  }

  fun <T> iteratePostIndexes(
    input: Collection<T>,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    postDescriptorSelector: (T) -> PostDescriptor,
    iterator: (ChanPost, Int) -> Unit
  ) {
    lock.read {
      input.forEach { inputValue ->
        val postDescriptor = postDescriptorSelector(inputValue)

        check(postDescriptor.threadDescriptor() == threadDescriptor) {
          "All posts must belong to the same thread! threadDescriptor=$threadDescriptor, " +
            "postDescriptor.threadDescriptor=${postDescriptor.threadDescriptor()}"
        }

        val postIndex = threadPosts
          .indexOfFirst { chanPost -> chanPost.postDescriptor == postDescriptor }

        if (postIndex < 0) {
          return@forEach
        }

        val chanPost = threadPosts.getOrNull(postIndex)
          ?: return@forEach

        iterator(chanPost, postIndex)
      }
    }
  }

  fun getNewPostsCount(lastPostNo: Long): Int {
    return lock.read { threadPosts.count { chanPost -> chanPost.postNo() > lastPostNo } }
  }

  fun findPostWithRepliesRecursive(
    postDescriptor: PostDescriptor,
    postsSet: MutableSet<ChanPost>
  ) {
    val postsToCheck = mutableListOf<ChanPost>()

    lock.read {
      for (post in threadPosts) {
        if (post.postDescriptor != postDescriptor || postsSet.contains(post)) {
          continue
        }

        postsToCheck.add(post)
      }
    }

    for (post in postsToCheck) {
      if (postsSet.contains(post)) {
        continue
      }

      postsSet.add(post)
      val repliesFromCopy = post.repliesFromCopy

      repliesFromCopy.forEach { lookUpPostDescriptor ->
        findPostWithRepliesRecursive(lookUpPostDescriptor, postsSet)
      }
    }
  }

  fun deletePosts(postDescriptors: Collection<PostDescriptor>) {
    lock.write {
      require(threadPosts.isNotEmpty()) { "posts are empty!" }
      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      postDescriptors.forEach { postDescriptor ->
        if (postDescriptor.isOP()) {
          Logger.e(TAG, "Deleting original post ${postDescriptor}!!! This may end up very badly!")
        }

        val postIndex = threadPosts.indexOfFirst { chanPost ->
          chanPost.postDescriptor == postDescriptor
        }

        if (postIndex >= 0) {
          threadPosts.removeAt(postIndex)
        }

        rawPostHashesMap.remove(postDescriptor)
        postsByPostDescriptors.remove(postDescriptor)
      }

      checkPostsConsistency()
    }
  }

  fun iteratePostsOrdered(iterator: (ChanPost) -> Unit) {
    iteratePostsOrderedWhile { chanPost ->
      iterator(chanPost)
      return@iteratePostsOrderedWhile true
    }
  }

  fun iteratePostsOrderedWhile(iterator: (ChanPost) -> Boolean) {
    lock.read {
      if (threadPosts.isEmpty()) {
        return@read
      }

      for (index in threadPosts.indices) {
        val chanPost = threadPosts.getOrNull(index)
          ?: return@read

        if (!iterator(chanPost)) {
          return@read
        }
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

  fun iteratePostImages(
    postDescriptor: PostDescriptor,
    iterator: (ChanPostImage) -> Unit
  ): Boolean {
    return lock.read {
      val post = postsByPostDescriptors[postDescriptor]
        ?: return@read false

      post.iteratePostImages { postImage -> iterator(postImage) }
      return@read true
    }
  }


  fun getThreadPostImages(): List<ChanPostImage> {
    return lock.read {
      val resultList = mutableListWithCap<ChanPostImage>(threadPosts.size)

      threadPosts.forEach { chanPost ->
        resultList.addAll(chanPost.postImages)
      }

      return@read resultList
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

  fun cleanup() {
    lock.write {
      val chanOriginalPost = threadPosts.firstOrNull()
      if (chanOriginalPost == null) {
        threadPosts.clear()
        postsByPostDescriptors.clear()
        rawPostHashesMap.clear()

        return@write
      }

      val onlyHasOriginalPost = threadPosts.size == 1 && threadPosts.firstOrNull() === chanOriginalPost
      if (onlyHasOriginalPost) {
        return@write
      }

      threadPosts.mutableIteration { mutableIterator, chanPost ->
        if (chanPost !== chanOriginalPost) {
          mutableIterator.remove()
        }

        return@mutableIteration true
      }

      postsByPostDescriptors.mutableIteration { mutableIterator, entry ->
        val chanPost = entry.value
        if (chanPost !== chanOriginalPost) {
          mutableIterator.remove()
        }

        return@mutableIteration true
      }

      rawPostHashesMap.mutableIteration { mutableIterator, entry ->
        val postDescriptor = entry.key
        if (postDescriptor != chanOriginalPost.postDescriptor) {
          mutableIterator.remove()
        }

        return@mutableIteration true
      }
    }
  }

  private fun updateThreadStatusFlagsForUi(
    oldChanOriginalPost: ChanOriginalPost,
    newChanOriginalPost: ChanOriginalPost
  ) {
    require(lock.isWriteLocked) { "Lock must be write locked!" }

    if (oldChanOriginalPost.sticky != newChanOriginalPost.sticky) {
      isNowStickyForUi = newChanOriginalPost.sticky
    }
    if (oldChanOriginalPost.closed != newChanOriginalPost.closed) {
      isNowClosedForUi = newChanOriginalPost.closed
    }
    if (oldChanOriginalPost.isDeleted != newChanOriginalPost.isDeleted) {
      isNowDeletedForUi = newChanOriginalPost.isDeleted
    }
    if (oldChanOriginalPost.archived != newChanOriginalPost.archived) {
      isNowArchivedForUi = newChanOriginalPost.archived
    }
  }

  private fun findDeletedPosts(
    oldChanPosts: MutableList<ChanPost>,
    postsFromServerData: PostsFromServerData?
  ): Set<PostDescriptor>? {
    if (postsFromServerData == null
      || postsFromServerData.isIncrementalUpdate
      || !postsFromServerData.isUpdatingDataFromTheServer
    ) {
      return null
    }

    val newChanPostDescriptors = postsFromServerData.allPostDescriptors
    val deletedPosts = hashSetWithCap<PostDescriptor>(4)

    oldChanPosts.forEach { oldPost ->
      if (oldPost.postDescriptor !in newChanPostDescriptors) {
        deletedPosts += oldPost.postDescriptor
      }
    }

    return deletedPosts
  }

  private fun mergePosts(
    oldChanPost: ChanPost,
    newChanPost: ChanPost,
    deletedPostsSet: Set<PostDescriptor>?
  ): ChanPost {
    if (oldChanPost is ChanOriginalPost || newChanPost is ChanOriginalPost) {
      return mergeOriginalPosts(oldChanPost, newChanPost, deletedPostsSet)
    }

    check(oldChanPost.postDescriptor == newChanPost.postDescriptor) {
      "Post descriptors differ!"
    }

    val postCommentsDiffer = postCommentsDiffer(oldChanPost, newChanPost)

    val mergedPost = ChanPost(
      chanPostId = oldChanPost.chanPostId,
      postDescriptor = oldChanPost.postDescriptor,
      repliesFrom = oldChanPost.repliesFrom,
      _postImages = mergePostImages(newChanPost.postImages, oldChanPost.postImages).toMutableList(),
      postIcons = newChanPost.postIcons,
      repliesTo = newChanPost.repliesTo,
      timestamp = newChanPost.timestamp,
      postComment = mergePostComments(oldChanPost.postComment, newChanPost.postComment),
      subject = newChanPost.subject,
      tripcode = newChanPost.tripcode,
      name = newChanPost.name,
      posterId = newChanPost.posterId,
      posterIdColor = newChanPost.posterIdColor,
      moderatorCapcode = newChanPost.moderatorCapcode,
      isSavedReply = newChanPost.isSavedReply,
      isSage = newChanPost.isSage,
      deleted = oldChanPost.isDeleted || (deletedPostsSet?.contains(oldChanPost.postDescriptor) == true)
    )

    handlePostContentLoadedMap(mergedPost, oldChanPost, postCommentsDiffer)
    return mergedPost
  }

  private fun mergeOriginalPosts(
    oldChanPost: ChanPost,
    newPost: ChanPost,
    deletedPostsSet: Set<PostDescriptor>?
  ): ChanOriginalPost {
    check(oldChanPost is ChanOriginalPost) { "oldChanPost is not ChanOriginalPost" }
    check(newPost is ChanOriginalPost) { "newPost is not ChanOriginalPost" }

    val oldChanOriginalPost = oldChanPost as ChanOriginalPost
    val newChanOriginalPost = newPost as ChanOriginalPost

    check(oldChanOriginalPost.postDescriptor == newChanOriginalPost.postDescriptor) {
      "Post descriptors differ!"
    }

    val postCommentsDiffer = postCommentsDiffer(oldChanOriginalPost, newChanOriginalPost)

    val mergedOriginalPost = ChanOriginalPost(
      chanPostId = oldChanOriginalPost.chanPostId,
      postDescriptor = oldChanOriginalPost.postDescriptor,
      repliesFrom = oldChanOriginalPost.repliesFrom,
      postImages = mergePostImages(newChanOriginalPost.postImages, oldChanOriginalPost.postImages),
      postIcons = newChanOriginalPost.postIcons,
      repliesTo = newChanOriginalPost.repliesTo,
      postComment = mergePostComments(oldChanOriginalPost.postComment, newChanOriginalPost.postComment),
      subject = newChanOriginalPost.subject,
      tripcode = newChanOriginalPost.tripcode,
      name = newChanOriginalPost.name,
      posterId = newChanOriginalPost.posterId,
      posterIdColor = newChanOriginalPost.posterIdColor,
      moderatorCapcode = newChanOriginalPost.moderatorCapcode,
      isSavedReply = newChanOriginalPost.isSavedReply,
      catalogRepliesCount = Math.max(oldChanOriginalPost.catalogRepliesCount, newChanOriginalPost.catalogRepliesCount),
      catalogImagesCount = Math.max(oldChanOriginalPost.catalogImagesCount, newChanOriginalPost.catalogImagesCount),
      timestamp = Math.max(oldChanOriginalPost.timestamp, newChanOriginalPost.timestamp),
      uniqueIps = Math.max(oldChanOriginalPost.uniqueIps, newChanOriginalPost.uniqueIps),
      lastModified = Math.max(oldChanOriginalPost.lastModified, newChanOriginalPost.lastModified),
      sticky = newChanOriginalPost.sticky,
      closed = newChanOriginalPost.closed,
      archived = newChanOriginalPost.archived,
      endless = newChanOriginalPost.endless,
      isSage = newChanOriginalPost.isSage,
      deleted = oldChanOriginalPost.isDeleted
        || (deletedPostsSet != null && deletedPostsSet.contains(oldChanOriginalPost.postDescriptor))
    )

    handlePostContentLoadedMap(mergedOriginalPost, oldChanOriginalPost, postCommentsDiffer)
    return mergedOriginalPost
  }

  private fun mergePostComments(oldPostComment: PostComment, newPostComment: PostComment): PostComment {
    if (oldPostComment.originalCommentHash == newPostComment.originalCommentHash) {
      return oldPostComment
    }

    return newPostComment
  }

  private fun postCommentsDiffer(oldChanPost: ChanPost, newChanPost: ChanPost): Boolean {
    return oldChanPost.postComment.originalCommentHash != newChanPost.postComment.originalCommentHash
  }

  private fun mergePostImages(
    newPostImages: List<ChanPostImage>,
    oldPostImages: List<ChanPostImage>
  ): List<ChanPostImage> {
    if (!ChanPostUtils.postImagesDiffer(newPostImages, oldPostImages)) {
      return oldPostImages
    }

    val resultList = mutableListWithCap<ChanPostImage>(newPostImages.size)
    resultList.addAll(newPostImages)

    resultList.forEach { newPostImage ->
      val oldPostImage = oldPostImages
        .firstOrNull { postImage -> postImage.equalUrl(newPostImage) }
        ?: return@forEach

      newPostImage.isPrefetched = oldPostImage.isPrefetched

      if (oldPostImage.loadedFileSize != null) {
        newPostImage.setSize(oldPostImage.loadedFileSize!!)
      }
    }

    oldPostImages.forEach { oldPostImage ->
      val alreadyContains = resultList.any { postImage ->
        postImage.serverFilename == oldPostImage.serverFilename
          && postImage.isInlined == oldPostImage.isInlined
      }

      if (alreadyContains) {
        return@forEach
      }

      resultList += oldPostImage
    }

    return resultList
  }

  private fun handlePostContentLoadedMap(
    mergedPost: ChanPost,
    oldChanPost: ChanPost,
    postCommentsDiffer: Boolean
  ) {
    mergedPost.replaceOnDemandContentLoadedArray(oldChanPost.copyOnDemandContentLoadedArray())

    if (postCommentsDiffer) {
      mergedPost.setContentLoadedForLoader(LoaderType.PostExtraContentLoader, false)
    }
  }

  private fun recalculatePostReplies(newChanPosts: List<ChanPost>) {
    require(lock.isWriteLocked) { "Lock must be write locked!" }

    val replies = HashMap<PostDescriptor, MutableList<PostDescriptor>>(threadPosts.size)

    for (newChanPost in newChanPosts) {
      val sourcePost = postsByPostDescriptors[newChanPost.postDescriptor]
        ?: continue

      for (replyTo in sourcePost.repliesTo) {
        if (postsByPostDescriptors[replyTo]?.repliesFrom?.contains(sourcePost.postDescriptor) == true) {
          // Already processed
          continue
        }

        var value = replies[replyTo]

        if (value == null) {
          value = ArrayList(3)
          replies[replyTo] = value
        }

        value.add(sourcePost.postDescriptor)
      }
    }

    for ((postNo, replyList) in replies) {
      val subject = postsByPostDescriptors[postNo]

      subject?.repliesFrom?.addAll(replyList)
    }
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

      threadPosts.forEach { chanPost1 ->
        val chanPost2 = postsByPostDescriptors[chanPost1.postDescriptor]

        if (chanPost1 is ChanOriginalPost) {
          check(chanPost1.lastModified >= 0L) { "Bad lastModified" }
        }

        if (chanPost2 is ChanOriginalPost) {
          check(chanPost2.lastModified >= 0L) { "Bad lastModified" }
        }

        checkNotNull(chanPost2) { "postsByPostDescriptors does not contain $chanPost1" }
        check(chanPost1 == chanPost2) { "Posts do not match (chanPost1=$chanPost1, chanPost2=$chanPost2)" }

        check(chanPost1.postDescriptor.descriptor is ChanDescriptor.ThreadDescriptor) {
          "Only thread descriptors are allowed in the cache!" +
            "descriptor=${chanPost1.postDescriptor.descriptor}"
        }

        check(chanPost2.postDescriptor.descriptor is ChanDescriptor.ThreadDescriptor) {
          "Only thread descriptors are allowed in the cache!" +
            "descriptor=${chanPost2.postDescriptor.descriptor}"
        }
      }
    }
  }

  private fun postsAreSorted(): Boolean {
    require(lock.isWriteLocked) { "Lock must be write locked!" }

    for (threadPostWindow in threadPosts.windowed(size = 2, step = 1)) {
      val prevPost = threadPostWindow.getOrNull(0) ?: break
      val currPost = threadPostWindow.getOrNull(1) ?: break

      if (currPost.postNo() < prevPost.postNo()) {
        return false
      }

      if (currPost.postNo() == prevPost.postNo() && currPost.postSubNo() < prevPost.postSubNo()) {
        return false
      }
    }

    return true
  }

  fun slicePosts(vararg rangesArg: IntRange): List<ChanPost> {
    require(rangesArg.isNotEmpty()) { "ranges must not be empty" }

    val ranges = rangesArg.toList()
    val totalCount = ranges.sumBy { range -> Math.max(0, range.last - range.first) }

    if (totalCount == 0) {
      return emptyList()
    }

    val duplicatesSet = hashSetWithCap<PostDescriptor>(totalCount)
    val resultList = mutableListWithCap<ChanPost>(totalCount)

    lock.read {
      ranges.forEach { range ->
        range.forEach { index ->
          val post = threadPosts.getOrNull(index)
            ?: return@forEach

          if (!duplicatesSet.add(post.postDescriptor)) {
            return@forEach
          }

          resultList += post
        }
      }
    }

    return resultList
  }

  fun getPostWithRepliesToThisPost(postDescriptor: PostDescriptor): List<PostDescriptor> {
    return lock.read {
      val chanPost = postsByPostDescriptors[postDescriptor]
        ?: return@read emptyList()

      val resultPosts = mutableListOf<PostDescriptor>()
      resultPosts += chanPost.postDescriptor
      resultPosts.addAll(chanPost.repliesFrom)
      return@read resultPosts
    }
  }

  fun isThreadLockCurrentlyLocked(): Boolean {
    return lock.isWriteLocked && !lock.isWriteLockedByCurrentThread
  }

  companion object {
    private const val TAG = "ChanThread"

    private val POSTS_COMPARATOR = Comparator<ChanPost> { chanPost1, chanPost2 ->
      // Due to a strange thread on Lainchan where OP has postNo greater that the next post after it we
      //  need to add a new step to this comparator which will force OP to be the very first post of
      //  the thread. (https://lainchan.org/%CE%A9/res/36474.html)
      if (chanPost1.isOP() && !chanPost2.isOP()) {
        return@Comparator -1
      } else if (!chanPost1.isOP() && chanPost2.isOP()) {
        return@Comparator 1
      }

      val postNoResult = chanPost1.postDescriptor.postNo.compareTo(chanPost2.postDescriptor.postNo)
      if (postNoResult != 0) {
        return@Comparator postNoResult
      }

      return@Comparator chanPost1.postDescriptor.postSubNo.compareTo(chanPost2.postDescriptor.postSubNo)
    }
  }
}