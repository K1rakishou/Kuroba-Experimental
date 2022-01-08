package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.features.thirdeye.ThirdEyeManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.post.ChanPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class ExtractPostMapInfoHolderUseCase(
  private val savedReplyManager: SavedReplyManager,
  private val siteManager: SiteManager,
  private val chanThreadManager: ChanThreadManager,
  private val postFilterManager: PostFilterManager,
  private val chanFilterManager: ChanFilterManager,
  private val thirdEyeManager: ThirdEyeManager
) : ISuspendUseCase<ExtractPostMapInfoHolderUseCase.Params, PostMapInfoHolder> {

  override suspend fun execute(parameter: Params): PostMapInfoHolder {
    return withContext(Dispatchers.IO) {
      val postDescriptors = parameter.postDescriptors

      val postsMap = chanThreadManager.getPosts(postDescriptors)
        .associateBy { it.postDescriptor }

      return@withContext PostMapInfoHolder(
        myPostsPositionRanges = extractMyPostsPositionsFromPostList(parameter),
        replyPositionRanges = extractReplyPositionsFromPostList(parameter, postsMap),
        crossThreadQuotePositionRanges = extractCrossThreadReplyPositionsFromPostList(parameter, postsMap),
        postFilterHighlightRanges = extractPostFilterHighlightsFromPostList(parameter),
        deletedPostsPositionRanges = extractDeletedPostsPositionsFromPostList(parameter, postsMap),
        hotPostsPositionRanges = extractHotPostsPositionsFromPostList(parameter, postsMap),
        thirdEyePostsPositionRanges = extractThirdEyePostsPositionsFromPostList(parameter, postsMap)
      )
    }
  }

  private suspend fun extractThirdEyePostsPositionsFromPostList(
    params: Params,
    postsMap: Map<PostDescriptor, ChanPost>
  ): List<PostMapInfoEntry> {
    BackgroundUtils.ensureBackgroundThread()

    if (!thirdEyeManager.isEnabled()) {
      return emptyList()
    }

    val postDescriptors = params.postDescriptors
    if (postDescriptors.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList()
    val duplicateChecker: MutableSet<Int> = HashSet()
    var prevIndex = 0

    for ((index, postDescriptor) in postDescriptors.withIndex()) {
      if (!duplicateChecker.add(index)) {
        continue
      }

      val post = postsMap[postDescriptor]
        ?: continue

      val postImages = post.postImages

      val hasThirdEyeImageHash = postImages.any { chanPostImage ->
        if (chanPostImage.isInlined) {
          return@any false
        }

        return@any thirdEyeManager.extractThirdEyeHashOrNull(chanPostImage) != null
      }

      if (!hasThirdEyeImageHash) {
        continue
      }

      connectRangesIfContiguous(prevIndex, index, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractHotPostsPositionsFromPostList(
    params: Params,
    postsMap: Map<PostDescriptor, ChanPost>
  ): List<PostMapInfoEntry> {
    BackgroundUtils.ensureBackgroundThread()

    if (!ChanSettings.markHotPostsOnScrollbar.get()) {
      return emptyList()
    }

    val postDescriptors = params.postDescriptors
    if (postDescriptors.isEmpty()) {
      return emptyList()
    }

    var totalPostsWithRepliesCount = 0
    var totalReplyCount = 0

    postsMap.values.forEach { chanPost ->
      if (chanPost.isOP()) {
        // Ignore the OP here
        return@forEach
      }

      val repliesFromCount = chanPost.repliesFromCount
      if (repliesFromCount <= 0) {
        // Do not include posts with 0 replies
        return@forEach
      }

      totalReplyCount += repliesFromCount
      ++totalPostsWithRepliesCount
    }

    if (totalReplyCount <= 0 || totalPostsWithRepliesCount <= 0) {
      return emptyList()
    }

    val medianRepliesCount = totalReplyCount.toFloat() / totalPostsWithRepliesCount.toFloat()
    if (medianRepliesCount <= 1f) {
      return emptyList()
    }

    // We consider posts HOT if their reply count is 3x higher than the median reply count in the thread
    val targetRepliesCount = medianRepliesCount * 3f

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList()
    val duplicateChecker: MutableSet<Int> = HashSet()
    var prevIndex = 0

    for ((index, postDescriptor) in postDescriptors.withIndex()) {
      val post = postsMap[postDescriptor]
        ?: continue

      if (post.isOP() || post.repliesFromCount <= targetRepliesCount || !duplicateChecker.add(index)) {
        continue
      }

      connectRangesIfContiguous(prevIndex, index, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractDeletedPostsPositionsFromPostList(
    params: Params,
    postsMap: Map<PostDescriptor, ChanPost>
  ): List<PostMapInfoEntry> {
    BackgroundUtils.ensureBackgroundThread()

    if (!ChanSettings.markDeletedPostsOnScrollbar.get()) {
      return emptyList()
    }

    val postDescriptors = params.postDescriptors
    if (postDescriptors.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList()
    val duplicateChecker: MutableSet<Int> = HashSet()
    var prevIndex = 0

    for ((index, postDescriptor) in postDescriptors.withIndex()) {
      val post = postsMap[postDescriptor]
        ?: continue

      if (!post.isDeleted || !duplicateChecker.add(index)) {
        continue
      }

      connectRangesIfContiguous(prevIndex, index, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractPostFilterHighlightsFromPostList(params: Params): List<PostMapInfoEntry> {
    BackgroundUtils.ensureBackgroundThread()

    val postDescriptors = params.postDescriptors
    if (postDescriptors.isEmpty()) {
      return emptyList()
    }

    val filtersMap = chanFilterManager.getEnabledHighlightFilters()
      .associateBy { chanFilter -> chanFilter.getDatabaseId() }
    if (filtersMap.isEmpty()) {
      return emptyList()
    }

    val filterHighlightsMap = postFilterManager.getManyFilterHighlights(postDescriptors)
    if (filterHighlightsMap.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList()
    val duplicateChecker: MutableSet<Int> = HashSet()
    var prevIndex = 0

    for ((index, post) in postDescriptors.withIndex()) {
      val filterHighlight = filterHighlightsMap[post]
        ?: continue

      if (filterHighlight.ownerFilterId == null
        || filterHighlight.highlightedColor == 0
        || !duplicateChecker.add(index)
      ) {
        continue
      }

      val chanFilter = filtersMap[filterHighlight.ownerFilterId]
      if (chanFilter?.action != FilterAction.COLOR.id) {
        continue
      }

      connectRangesIfContiguousWithColor(prevIndex, index, chanFilter.color, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractMyPostsPositionsFromPostList(params: Params): List<PostMapInfoEntry> {
    BackgroundUtils.ensureBackgroundThread()

    if (!ChanSettings.markYourPostsOnScrollbar.get()) {
      return emptyList()
    }

    val postDescriptors = params.postDescriptors
    val isViewingThread = params.isViewingThread

    if (postDescriptors.isEmpty() || !isViewingThread) {
      return emptyList()
    }

    val siteDescriptor = postDescriptors.first().siteDescriptor()
    if (siteManager.bySiteDescriptor(siteDescriptor) == null) {
      return emptyList()
    }

    val threadDescriptor = postDescriptors.first().threadDescriptor()
    val savedPostDescriptorSet = HashSet(
      savedReplyManager.retainSavedPostNoMap(postDescriptors, threadDescriptor)
    )

    if (savedPostDescriptorSet.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList(savedPostDescriptorSet.size)
    val duplicateChecker: MutableSet<Int> = HashSet(savedPostDescriptorSet.size)
    var prevIndex = 0

    for ((index, postDescriptor) in postDescriptors.withIndex()) {
      if (!savedPostDescriptorSet.contains(postDescriptor) || !duplicateChecker.add(index)) {
        continue
      }

      connectRangesIfContiguous(prevIndex, index, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractReplyPositionsFromPostList(
    params: Params,
    postsMap: Map<PostDescriptor, ChanPost>
  ): List<PostMapInfoEntry> {
    BackgroundUtils.ensureBackgroundThread()

    if (!ChanSettings.markRepliesToYourPostOnScrollbar.get()) {
      return emptyList()
    }

    val postDescriptors = params.postDescriptors
    val isViewingThread = params.isViewingThread

    if (postDescriptors.isEmpty() || !isViewingThread) {
      return emptyList()
    }

    val siteDescriptor = postDescriptors.first().siteDescriptor()
    if (siteManager.bySiteDescriptor(siteDescriptor) == null) {
      return emptyList()
    }

    val threadDescriptor = postDescriptors.first().threadDescriptor()
    val savedPostNoSet = HashSet(
      savedReplyManager.retainSavedPostNoMap(postDescriptors, threadDescriptor)
    )

    if (savedPostNoSet.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList(savedPostNoSet.size)
    val duplicateChecker: MutableSet<Int> = HashSet(savedPostNoSet.size)
    var prevIndex = 0

    for ((index, postDescriptor) in postDescriptors.withIndex()) {
      val post = postsMap[postDescriptor]
        ?: continue

      for (replyTo in post.repliesTo) {
        if (!savedPostNoSet.contains(replyTo) || !duplicateChecker.add(index)) {
          continue
        }

        connectRangesIfContiguous(prevIndex, index, replyRanges)
        prevIndex = index
        break
      }
    }

    return replyRanges
  }

  private fun extractCrossThreadReplyPositionsFromPostList(params: Params, postsMap: Map<PostDescriptor, ChanPost>): List<PostMapInfoEntry> {
    BackgroundUtils.ensureBackgroundThread()

    if (!ChanSettings.markCrossThreadQuotesOnScrollbar.get()) {
      return emptyList()
    }

    val postDescriptors = params.postDescriptors
    val isViewingThread = params.isViewingThread

    if (postDescriptors.isEmpty() || !isViewingThread) {
      return emptyList()
    }

    val crossThreadReplyRanges: MutableList<PostMapInfoEntry> = ArrayList(16)
    val duplicateChecker: MutableSet<Int> = HashSet(16)
    var prevIndex = 0

    for ((index, postDescriptor) in postDescriptors.withIndex()) {
      val post = postsMap[postDescriptor]
        ?: continue

      for (postLinkable in post.postComment.linkables) {
        if (postLinkable.type != PostLinkable.Type.THREAD || !duplicateChecker.add(index)) {
          continue
        }

        connectRangesIfContiguous(prevIndex, index, crossThreadReplyRanges)
        prevIndex = index
        break
      }
    }

    return crossThreadReplyRanges
  }

  private fun connectRangesIfContiguous(prevIndex: Int, index: Int, ranges: MutableList<PostMapInfoEntry>) {
    if (prevIndex == index - 1 && ranges.size > 0) {
      val prevRange = ranges[ranges.lastIndex].range

      ranges[ranges.lastIndex] = PostMapInfoEntry(IntRange(prevRange.first, index), 0)
    } else {
      ranges.add(PostMapInfoEntry(IntRange(index, index), 0))
    }
  }

  private fun connectRangesIfContiguousWithColor(
    prevIndex: Int,
    index: Int,
    newColor: Int,
    ranges: MutableList<PostMapInfoEntry>
  ) {
    if (prevIndex == index - 1 && ranges.size > 0) {
      val prevColor = ranges[ranges.lastIndex].color
      if (prevColor == newColor) {
        val prevRange = ranges[ranges.lastIndex].range
        ranges[ranges.lastIndex] = PostMapInfoEntry(IntRange(prevRange.first, index), newColor)
        return
      }

      // fallthrough
    }

    ranges.add(PostMapInfoEntry(IntRange(index, index), newColor))
  }

  data class Params(
    val postDescriptors: List<PostDescriptor>,
    val isViewingThread: Boolean
  )

}

data class PostMapInfoHolder(
  val myPostsPositionRanges: List<PostMapInfoEntry> = emptyList(),
  val replyPositionRanges: List<PostMapInfoEntry> = emptyList(),
  val crossThreadQuotePositionRanges: List<PostMapInfoEntry> = emptyList(),
  val postFilterHighlightRanges: List<PostMapInfoEntry> = emptyList(),
  val deletedPostsPositionRanges: List<PostMapInfoEntry> = emptyList(),
  val hotPostsPositionRanges: List<PostMapInfoEntry> = emptyList(),
  val thirdEyePostsPositionRanges: List<PostMapInfoEntry> = emptyList()
) {

  fun isEmpty(): Boolean {
    return myPostsPositionRanges.isEmpty()
      && replyPositionRanges.isEmpty()
      && crossThreadQuotePositionRanges.isEmpty()
      && postFilterHighlightRanges.isEmpty()
      && deletedPostsPositionRanges.isEmpty()
      && hotPostsPositionRanges.isEmpty()
      && thirdEyePostsPositionRanges.isEmpty()
  }

  fun isTheSame(otherPostMapInfoHolder: PostMapInfoHolder): Boolean {
    if (!rangesTheSame(myPostsPositionRanges, otherPostMapInfoHolder.myPostsPositionRanges)) {
      return false
    }

    if (!rangesTheSame(replyPositionRanges, otherPostMapInfoHolder.replyPositionRanges)) {
      return false
    }

    if (!rangesTheSame(crossThreadQuotePositionRanges, otherPostMapInfoHolder.crossThreadQuotePositionRanges)) {
      return false
    }

    if (!rangesTheSame(postFilterHighlightRanges, otherPostMapInfoHolder.postFilterHighlightRanges)) {
      return false
    }

    if (!rangesTheSame(deletedPostsPositionRanges, otherPostMapInfoHolder.deletedPostsPositionRanges)) {
      return false
    }

    if (!rangesTheSame(hotPostsPositionRanges, otherPostMapInfoHolder.hotPostsPositionRanges)) {
      return false
    }

    if (!rangesTheSame(thirdEyePostsPositionRanges, otherPostMapInfoHolder.thirdEyePostsPositionRanges)) {
      return false
    }

    return true
  }

  private fun rangesTheSame(ranges1: List<PostMapInfoEntry>, ranges2: List<PostMapInfoEntry>): Boolean {
    if (ranges1.size != ranges2.size) {
      return false
    }

    if (ranges1.isEmpty() && ranges2.isEmpty()) {
      return true
    }

    for ((index, newPosition) in ranges2.withIndex()) {
      val oldPosition = ranges1[index]
      if (newPosition != oldPosition) {
        return false
      }
    }

    return true
  }

}

data class PostMapInfoEntry(
  val range: IntRange,
  val color: Int
)