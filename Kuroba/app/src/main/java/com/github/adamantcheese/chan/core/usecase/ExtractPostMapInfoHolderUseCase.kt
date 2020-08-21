package com.github.adamantcheese.chan.core.usecase

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import java.util.*

class ExtractPostMapInfoHolderUseCase(
  private val databaseSavedReplyManager: DatabaseSavedReplyManager,
  private val siteManager: SiteManager
) : IUseCase<List<Post>, PostMapInfoHolder> {

  override fun execute(parameter: List<Post>): PostMapInfoHolder {
    return PostMapInfoHolder(
      extractArchivedPostsPositionsFromPostList(parameter),
      extractMyPostsPositionsFromPostList(parameter),
      extractReplyPositionsFromPostList(parameter),
      extractCrossThreadReplyPositionsFromPostList(parameter)
    )
  }

  private fun extractArchivedPostsPositionsFromPostList(posts: List<Post>): List<IntRange> {
    if (!ChanSettings.markArchivedPostsOnScrollbar.get()) {
      return emptyList()
    }

    val allPostsFromArchive = posts.all { post -> post.archiveDescriptor != null }
    if (allPostsFromArchive) {
      return emptyList()
    }

    val replyRanges: MutableList<IntRange> = ArrayList()
    val duplicateChecker: MutableSet<Int> = HashSet()
    var prevIndex = 0

    for ((index, post) in posts.withIndex()) {
      if (post.archiveDescriptor == null || !duplicateChecker.add(index)) {
        continue
      }

      connectRangesIfContiguous(prevIndex, index, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractMyPostsPositionsFromPostList(posts: List<Post>): List<IntRange> {
    if (!ChanSettings.markYourPostsOnScrollbar.get()) {
      return emptyList()
    }

    if (posts.isEmpty()) {
      return emptyList()
    }

    val siteDescriptor = posts.first().boardDescriptor.siteDescriptor
    if (siteManager.bySiteDescriptor(siteDescriptor) == null) {
      return emptyList()
    }

    val savedPostNoSet: Set<Long> = HashSet(databaseSavedReplyManager.retainSavedPostNos(posts, siteDescriptor))
    if (savedPostNoSet.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<IntRange> = ArrayList(savedPostNoSet.size)
    val duplicateChecker: MutableSet<Int> = HashSet(savedPostNoSet.size)
    var prevIndex = 0

    for ((index, post) in posts.withIndex()) {
      if (!savedPostNoSet.contains(post.no) || !duplicateChecker.add(index)) {
        continue
      }

      connectRangesIfContiguous(prevIndex, index, replyRanges)
      prevIndex = index
    }

    return replyRanges
  }

  private fun extractReplyPositionsFromPostList(posts: List<Post>): List<IntRange> {
    if (!ChanSettings.markRepliesToYourPostOnScrollbar.get()) {
      return emptyList()
    }

    if (posts.isEmpty()) {
      return emptyList()
    }

    val siteDescriptor = posts.first().boardDescriptor.siteDescriptor
    if (siteManager.bySiteDescriptor(siteDescriptor) == null) {
      return emptyList()
    }

    val savedPostNoSet: Set<Long> = HashSet(databaseSavedReplyManager.retainSavedPostNos(posts, siteDescriptor))
    if (savedPostNoSet.isEmpty()) {
      return emptyList()
    }

    val replyRanges: MutableList<IntRange> = ArrayList(savedPostNoSet.size)
    val duplicateChecker: MutableSet<Int> = HashSet(savedPostNoSet.size)
    var prevIndex = 0

    for ((index, post) in posts.withIndex()) {
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

  private fun extractCrossThreadReplyPositionsFromPostList(posts: List<Post>): List<IntRange> {
    if (!ChanSettings.markCrossThreadQuotesOnScrollbar.get()) {
      return emptyList()
    }

    val crossThreadReplyRanges: MutableList<IntRange> = ArrayList(16)
    val duplicateChecker: MutableSet<Int> = HashSet(16)
    var prevIndex = 0

    for ((index, post) in posts.withIndex()) {
      for (postLinkable in post.linkables) {
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
  val archivedPostsPositionRanges: List<IntRange> = emptyList(),
  val myPostsPositionRanges: List<IntRange> = emptyList(),
  val replyPositionRanges: List<IntRange> = emptyList(),
  val crossThreadQuotePositionRanges: List<IntRange> = emptyList()
) {

  fun isTheSame(otherPostMapInfoHolder: PostMapInfoHolder): Boolean {
    if (!rangesTheSame(archivedPostsPositionRanges, otherPostMapInfoHolder.archivedPostsPositionRanges)) {
      return false
    }

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