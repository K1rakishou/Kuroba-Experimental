package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.*

class ExtractPostMapInfoHolderUseCase(
  private val savedReplyManager: SavedReplyManager,
  private val siteManager: SiteManager,
  private val chanThreadManager: ChanThreadManager,
  private val postFilterManager: PostFilterManager
) : IUseCase<ExtractPostMapInfoHolderUseCase.Params, PostMapInfoHolder> {

  override fun execute(parameter: Params): PostMapInfoHolder {
    return PostMapInfoHolder(
      myPostsPositionRanges = extractMyPostsPositionsFromPostList(parameter),
      replyPositionRanges = extractReplyPositionsFromPostList(parameter),
      crossThreadQuotePositionRanges = extractCrossThreadReplyPositionsFromPostList(parameter),
      postFilterHighlightRanges = extractPostFilterHighlightsFromPostList(parameter),
      deletedPostsPositionRanges = extractDeletedPostsPositionsFromPostList(parameter)
    )
  }

  private fun extractDeletedPostsPositionsFromPostList(params: Params): List<PostMapInfoEntry> {
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
      val post = chanThreadManager.getPost(postDescriptor)
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
    val postDescriptors = params.postDescriptors
    if (postDescriptors.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList()
    val duplicateChecker: MutableSet<Int> = HashSet()
    val filterHighlightedColorsMap = postFilterManager.getManyFilterHighlightedColors(postDescriptors)
    var prevIndex = 0

    for ((index, post) in postDescriptors.withIndex()) {
      val filterColor = filterHighlightedColorsMap[post]
      if (filterColor == null || !duplicateChecker.add(index)) {
        continue
      }

      connectRangesIfContiguousWithColor(prevIndex, index, filterColor, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractMyPostsPositionsFromPostList(params: Params): List<PostMapInfoEntry> {
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
    val savedPostNoSet = HashSet(
      savedReplyManager.retainSavedPostNoMap(postDescriptors, threadDescriptor)
    )

    if (savedPostNoSet.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<PostMapInfoEntry> = ArrayList(savedPostNoSet.size)
    val duplicateChecker: MutableSet<Int> = HashSet(savedPostNoSet.size)
    var prevIndex = 0

    for ((index, post) in postDescriptors.withIndex()) {
      if (!savedPostNoSet.contains(post.postNo) || !duplicateChecker.add(index)) {
        continue
      }

      connectRangesIfContiguous(prevIndex, index, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractReplyPositionsFromPostList(params: Params): List<PostMapInfoEntry> {
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
      val post = chanThreadManager.getPost(postDescriptor)
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

  private fun extractCrossThreadReplyPositionsFromPostList(params: Params): List<PostMapInfoEntry> {
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
      val post = chanThreadManager.getPost(postDescriptor)
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
  val deletedPostsPositionRanges: List<PostMapInfoEntry> = emptyList()
) {

  fun isEmpty(): Boolean {
    return myPostsPositionRanges.isEmpty()
      && replyPositionRanges.isEmpty()
      && crossThreadQuotePositionRanges.isEmpty()
      && postFilterHighlightRanges.isEmpty()
      && deletedPostsPositionRanges.isEmpty()
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