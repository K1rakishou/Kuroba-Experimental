package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.*

class ExtractPostMapInfoHolderUseCase(
  private val savedReplyManager: SavedReplyManager,
  private val siteManager: SiteManager,
  private val chanThreadManager: ChanThreadManager
) : IUseCase<List<PostDescriptor>, PostMapInfoHolder> {

  override fun execute(parameter: List<PostDescriptor>): PostMapInfoHolder {
    return PostMapInfoHolder(
      extractMyPostsPositionsFromPostList(parameter),
      extractReplyPositionsFromPostList(parameter),
      extractCrossThreadReplyPositionsFromPostList(parameter)
    )
  }

  private fun extractMyPostsPositionsFromPostList(postDescriptors: List<PostDescriptor>): List<IntRange> {
    if (!ChanSettings.markYourPostsOnScrollbar.get()) {
      return emptyList()
    }

    if (postDescriptors.isEmpty()) {
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

    val replyRanges: MutableList<IntRange> = ArrayList(savedPostNoSet.size)
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

  private fun extractReplyPositionsFromPostList(postDescriptors: List<PostDescriptor>): List<IntRange> {
    if (!ChanSettings.markRepliesToYourPostOnScrollbar.get()) {
      return emptyList()
    }

    if (postDescriptors.isEmpty()) {
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

    val replyRanges: MutableList<IntRange> = ArrayList(savedPostNoSet.size)
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

  private fun extractCrossThreadReplyPositionsFromPostList(postDescriptors: List<PostDescriptor>): List<IntRange> {
    if (!ChanSettings.markCrossThreadQuotesOnScrollbar.get()) {
      return emptyList()
    }

    val crossThreadReplyRanges: MutableList<IntRange> = ArrayList(16)
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

  private fun connectRangesIfContiguous(prevIndex: Int, index: Int, ranges: MutableList<IntRange>) {
    if (prevIndex == index - 1 && ranges.size > 0) {
      val prevRange = ranges[ranges.lastIndex]
      ranges[ranges.lastIndex] = IntRange(prevRange.first, index)
    } else {
      ranges.add(IntRange(index, index))
    }
  }

}

data class PostMapInfoHolder(
  val myPostsPositionRanges: List<IntRange> = emptyList(),
  val replyPositionRanges: List<IntRange> = emptyList(),
  val crossThreadQuotePositionRanges: List<IntRange> = emptyList()
) {

  fun isEmpty(): Boolean {
    return myPostsPositionRanges.isEmpty()
      && replyPositionRanges.isEmpty()
      && crossThreadQuotePositionRanges.isEmpty()
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

    return true
  }

  private fun rangesTheSame(ranges1: List<IntRange>, ranges2: List<IntRange>): Boolean {
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