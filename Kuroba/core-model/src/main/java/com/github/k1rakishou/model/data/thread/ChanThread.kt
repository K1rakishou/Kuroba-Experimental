package com.github.k1rakishou.model.data.thread

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
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
  private val forceUpdateOnNextUpdateIteration = hashSetWithCap<PostDescriptor>(32)
  @GuardedBy("lock")
  private var lastAccessTime = initialLastAccessTime
  @GuardedBy("lock")
  private var lastUpdateTime = 0L

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

  fun getAllPostsForDatabasePersisting(): List<ChanPost> {
    return lock.read { threadPosts }
  }

  fun addOrUpdatePosts(newChanPosts: List<ChanPost>): Boolean {
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

      var addedOrUpdatedPosts = false
      var addedPostsCount = 0
      var updatedPostsCount = 0

      newChanPosts.forEach { newChanPost ->
        require(newChanPost.postDescriptor.descriptor is ChanDescriptor.ThreadDescriptor) {
          "postDescriptor.descriptor must be thread ThreadDescriptor"
        }

        // We don't have this post, just add it at the end
        if (!postsByPostDescriptors.containsKey(newChanPost.postDescriptor)) {
          threadPosts.add(newChanPost)
          postsByPostDescriptors[newChanPost.postDescriptor] = newChanPost
          forceUpdateOnNextUpdateIteration.remove(newChanPost.postDescriptor)

          addedOrUpdatedPosts = true
          addedPostsCount++

          return@forEach
        }

        val oldChanPostIndex = threadPosts
          .indexOfFirst { post -> post.postDescriptor == newChanPost.postDescriptor }
        check(oldChanPostIndex >= 0) { "Bad oldChanPostIndex: $oldChanPostIndex" }

        val oldChanPost = threadPosts[oldChanPostIndex]
        if (oldChanPost == newChanPost && oldChanPost.postDescriptor !in forceUpdateOnNextUpdateIteration) {
          return@forEach
        }

        // We already have this post, we need to merge old and new posts into one and replace old
        // post with the merged post
        val mergedPost = mergePosts(oldChanPost, newChanPost)

        threadPosts[oldChanPostIndex] = mergedPost
        postsByPostDescriptors[newChanPost.postDescriptor] = mergedPost
        forceUpdateOnNextUpdateIteration.remove(newChanPost.postDescriptor)

        addedOrUpdatedPosts = true
        ++updatedPostsCount
      }

      if (addedOrUpdatedPosts) {
        threadPosts.sortWith(POSTS_COMPARATOR)
        recalculatePostReplies()
      }

      checkPostsConsistency()

      Logger.d(TAG, "Thread cache (${threadDescriptor}) Added ${addedPostsCount} new posts, " +
        "updated ${updatedPostsCount} posts")

      return@write addedOrUpdatedPosts
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
        val mergedChanOriginalPost = mergePosts(oldChanOriginalPost, newChanOriginalPost)

        threadPosts[0] = mergedChanOriginalPost
        postsByPostDescriptors[newChanOriginalPost.postDescriptor] = mergedChanOriginalPost
      } else {
        threadPosts.add(newChanOriginalPost)
        postsByPostDescriptors[newChanOriginalPost.postDescriptor] = newChanOriginalPost

        threadPosts.sortWith(POSTS_COMPARATOR)
      }

      checkPostsConsistency()
    }
  }

  fun getOriginalPost(): ChanOriginalPost {
    lock.read {
      require(threadPosts.isNotEmpty()) { "posts are empty!" }
      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      return threadPosts.first() as ChanOriginalPost
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

  fun forceUpdatePosts(postDescriptors: Collection<PostDescriptor>) {
    lock.write {
      postDescriptors.forEach { postDescriptor ->
        if (postDescriptor.isOP()) {
          // No need since we always update original posts
          return@forEach
        }

        forceUpdateOnNextUpdateIteration.add(postDescriptor)
      }
    }
  }

  fun isForceUpdating(postDescriptor: PostDescriptor): Boolean {
    return lock.read { forceUpdateOnNextUpdateIteration.contains(postDescriptor) }
  }

  fun cacheNeedsUpdate(chanCacheUpdateOption: ChanCacheUpdateOptions): Boolean {
    when (chanCacheUpdateOption) {
      ChanCacheUpdateOptions.UpdateCache -> return true
      ChanCacheUpdateOptions.DoNotUpdateCache -> return false
      is ChanCacheUpdateOptions.UpdateIfCacheIsOlderThan -> {
        return lock.read { (System.currentTimeMillis() - lastUpdateTime) > chanCacheUpdateOption.timePeriodMs}
      }
    }
  }

  fun updateLastUpdateTime(cacheUpdateOptions: ChanCacheUpdateOptions) {
    lock.write {
      if (!cacheUpdateOptions.canUpdate(lastUpdateTime)) {
        return@write
      }

      lastUpdateTime = System.currentTimeMillis()
    }
  }

  fun setDeleted(deleted: Boolean) {
    lock.write {
      require(threadPosts.isNotEmpty()) { "posts are empty!" }
      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      threadPosts[0].setPostDeleted(deleted)
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

  fun deletePosts(postDescriptors: Collection<PostDescriptor>) {
    lock.write {
      require(threadPosts.isNotEmpty()) { "posts are empty!" }
      require(threadPosts.first() is ChanOriginalPost) {
        "First post is not an original post! post=${threadPosts.first()}"
      }

      postDescriptors.forEach { postDescriptor ->
        val postIndex = threadPosts.indexOfFirst { chanPost ->
          chanPost.postDescriptor == postDescriptor
        }

        if (postIndex >= 0) {
          threadPosts.removeAt(postIndex)
        }

        rawPostHashesMap.remove(postDescriptor)
        postsByPostDescriptors.remove(postDescriptor)
        forceUpdateOnNextUpdateIteration.remove(postDescriptor)
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

  private fun mergePosts(oldChanPost: ChanPost, newChanPost: ChanPost): ChanPost {
    if (oldChanPost is ChanOriginalPost || newChanPost is ChanOriginalPost) {
      return mergeOriginalPosts(oldChanPost, newChanPost)
    }

    check(oldChanPost.postDescriptor == newChanPost.postDescriptor) {
      "Post descriptors differ!"
    }

    val postCommentsDiffer = postCommentsDiffer(oldChanPost, newChanPost)

    val mergedPost = ChanPost(
      chanPostId = oldChanPost.chanPostId,
      postDescriptor = oldChanPost.postDescriptor,
      repliesFrom = oldChanPost.repliesFrom,
      postImages = mergePostImages(newChanPost.postImages, oldChanPost.postImages),
      postIcons = newChanPost.postIcons,
      repliesTo = newChanPost.repliesTo,
      timestamp = newChanPost.timestamp,
      postComment = mergePostComments(oldChanPost.postComment, newChanPost.postComment),
      subject = newChanPost.subject,
      tripcode = newChanPost.tripcode,
      name = newChanPost.name,
      posterId = newChanPost.posterId,
      moderatorCapcode = newChanPost.moderatorCapcode,
      isSavedReply = newChanPost.isSavedReply
    )

    handlePostContentLoadedMap(mergedPost, oldChanPost, postCommentsDiffer)
    mergedPost.setPostDeleted(oldChanPost.deleted)
    return mergedPost
  }

  private fun mergeOriginalPosts(
    oldChanPost: ChanPost,
    newPost: ChanPost
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
      moderatorCapcode = newChanOriginalPost.moderatorCapcode,
      isSavedReply = newChanOriginalPost.isSavedReply,
      catalogRepliesCount = Math.max(oldChanOriginalPost.catalogRepliesCount, newChanOriginalPost.catalogRepliesCount),
      catalogImagesCount = Math.max(oldChanOriginalPost.catalogImagesCount, newChanOriginalPost.catalogImagesCount),
      timestamp = Math.max(oldChanOriginalPost.timestamp, newChanOriginalPost.timestamp),
      uniqueIps = Math.max(oldChanOriginalPost.uniqueIps, newChanOriginalPost.uniqueIps),
      lastModified = Math.max(oldChanOriginalPost.lastModified, newChanOriginalPost.lastModified),
      sticky = newChanOriginalPost.sticky,
      closed = newChanOriginalPost.closed,
      archived = newChanOriginalPost.archived
    )

    handlePostContentLoadedMap(mergedOriginalPost, oldChanOriginalPost, postCommentsDiffer)
    mergedOriginalPost.setPostDeleted(oldChanOriginalPost.deleted)
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

    return newPostImages
  }

  private fun handlePostContentLoadedMap(
    mergedPost: ChanPost,
    oldChanPost: ChanPost,
    postCommentsDiffer: Boolean
  ) {
    mergedPost.replaceOnDemandContentLoadedMap(oldChanPost.copyOnDemandContentLoadedMap())

    if (postCommentsDiffer) {
      mergedPost.setContentLoadedForLoader(LoaderType.PostExtraContentLoader, false)
    }
  }

  private fun recalculatePostReplies() {
    require(lock.isWriteLocked) { "Lock must be write locked!" }

    val postsByNo: MutableMap<Long, ChanPost> = HashMap()
    for (post in threadPosts) {
      postsByNo[post.postNo()] = post
    }

    // Maps post no's to a list of no's that that post received replies from
    val replies: MutableMap<Long, MutableList<Long>> = HashMap()

    for (sourcePost in threadPosts) {
      for (replyTo in sourcePost.repliesTo) {
        var value = replies[replyTo]

        if (value == null) {
          value = ArrayList(3)
          replies[replyTo] = value
        }

        value.add(sourcePost.postNo())
      }
    }

    for ((postNo, replyList) in replies) {
      val subject = postsByNo[postNo]

      // Sometimes a post replies to a ghost, a post that doesn't exist.
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

      var prevPostNo = Long.MIN_VALUE

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

        // Lainchan allows OPs have postNo being greater than any other post in a thread. This must be
        //  considered a bug. On our side we have no other choice than to hack around it.
        if (!threadDescriptor.siteDescriptor().isLainchan()) {
          check(chanPost1.postNo() > prevPostNo) { "Posts are not sorted!" }
        }

        prevPostNo = chanPost1.postNo()
      }
    }
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

      val replies = chanPost.repliesFrom
        .map { replyFrom -> PostDescriptor.create(postDescriptor.descriptor, replyFrom) }

      resultPosts.addAll(replies)
      return@read resultPosts
    }
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