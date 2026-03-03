package com.github.k1rakishou.chan.core.site.parser.processor

import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanReaderProcessor(
  private val chanPostRepository: ChanPostRepository,
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier,
  private val chanReadOptions: ChanReadOptions,
  private val chanLoadOptions: ChanLoadOptions,
  private val options: Options,
  override val page: Int?,
  override val chanDescriptor: ChanDescriptor
) : AbstractChanReaderProcessor() {
  private val toParse = mutableListWithCap<ChanPostBuilder>(64)
  private val postOrderedList = mutableListWithCap<PostDescriptor>(64)

  private val lock = Mutex()

  val isIncrementalUpdate: Boolean
    get() = options.isIncrementalUpdate

  val allPostDescriptorsFromServer: Set<PostDescriptor>
    get() = postOrderedList.toSet()

  override suspend fun setOp(op: ChanPostBuilder?) {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    lock.withLock {
      if (op != null) {
        closed = op.closed
        archived = op.archived
        deleted = op.deleted
      }
    }
  }

  override suspend fun addPost(postBuilder: ChanPostBuilder) {
    val totalPostsRead = lock.withLock {
      if (differsFromCached(postBuilder)) {
        toParse.add(postBuilder)
      }

      postOrderedList.add(postBuilder.postDescriptor())
      return@withLock postOrderedList.size
    }

    chanLoadProgressNotifier.sendProgressEvent(
      ChanLoadProgressEvent.Reading(
        chanDescriptor = chanDescriptor,
        totalPostsRead = totalPostsRead
      )
    )
  }

  override suspend fun applyChanReadOptions() {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return
    }

    if (chanReadOptions.isDefault()) {
      return
    }

    lock.withLock {
      val postRanges = chanReadOptions.getRetainPostRanges(postOrderedList.size)
      val postDescriptorsToDelete = mutableSetOf<PostDescriptor>()

      Logger.d(TAG, "applyChanReadOptions(chanReadOptions=$chanReadOptions) " +
        "postsCount=${postOrderedList.size}, postRanges=$postRanges")

      for ((index, postDescriptor) in postOrderedList.withIndex()) {
        val anyRangeContainsThisPost = postRanges.any { postRange -> postRange.contains(index) }
        if (anyRangeContainsThisPost) {
          // At least one range contains this post's index, so we need to retain it
          continue
        }

        postDescriptorsToDelete += postDescriptor
      }

      if (postDescriptorsToDelete.isEmpty()) {
        return@withLock
      }

      Logger.d(TAG, "applyChanReadOptions() postDescriptorsToDelete=${postDescriptorsToDelete.size}")

      postOrderedList.removeAll(postDescriptorsToDelete)
      toParse.removeIfKt { postToParse -> postToParse.postDescriptor() in postDescriptorsToDelete }
    }
  }

  override suspend fun getToParse(): List<ChanPostBuilder> {
    return lock.withLock { toParse }
  }

  override suspend fun getThreadDescriptors(): List<ChanDescriptor.ThreadDescriptor> {
    return lock.withLock {
      return@withLock toParse
        .map { chanPostBuilder -> chanPostBuilder.postDescriptor().threadDescriptor() }
    }
  }

  override suspend fun getTotalPostsCount(): Int {
    return lock.withLock { postOrderedList.size }
  }

  private fun differsFromCached(builder: ChanPostBuilder): Boolean {
    if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      // Always update catalog posts
      return true
    }

    if (builder.op) {
      // Always update original post
      return true
    }

    if (options.isDownloadingThread) {
      return true
    }

    val postDescriptor = builder.postDescriptor()

    if (chanLoadOptions.isForceUpdating(postDescriptor)) {
      return true
    }

    val chanPost = chanPostRepository.getCachedPost(postDescriptor)
    if (chanPost == null) {
      chanPostRepository.putPostHash(postDescriptor, builder.getPostHash)
      return true
    }

    val cachedPostHash = chanPostRepository.getPostHash(postDescriptor)
    if (cachedPostHash == null) {
      chanPostRepository.putPostHash(postDescriptor, builder.getPostHash)
      return true
    }

    if (builder.getPostHash != cachedPostHash) {
      chanPostRepository.putPostHash(postDescriptor, builder.getPostHash)
      return true
    }

    val postsDiffer = ChanPostUtils.postsDiffer(
      chanPostBuilder = builder,
      chanPostFromCache = chanPost,
      isThreadMode = chanDescriptor is ChanDescriptor.ThreadDescriptor
    )

    if (postsDiffer) {
      return true
    }

    return false
  }

  override fun toString(): String {
    return "ChanReaderProcessor{chanDescriptor=$chanDescriptor, toParse=${toParse.size}, " +
      "closed=${closed}, deleted=${deleted}, archived=${archived}, error=${error}}"
  }

  data class Options(
    val isDownloadingThread: Boolean = false,
    val isIncrementalUpdate: Boolean = false
  )

  companion object {
    private const val TAG = "ChanReaderProcessor"
  }
}