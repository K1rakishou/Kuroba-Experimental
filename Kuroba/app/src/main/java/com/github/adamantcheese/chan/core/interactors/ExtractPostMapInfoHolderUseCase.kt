package com.github.adamantcheese.chan.core.interactors

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import java.util.*

class ExtractPostMapInfoHolderUseCase(
  private val databaseSavedReplyManager: DatabaseSavedReplyManager
) : IUseCase<List<Post>, PostMapInfoHolder> {

  override fun execute(parameter: List<Post>): PostMapInfoHolder {
    return PostMapInfoHolder(
      extractReplyPositionsFromPostList(parameter),
      extractCrossThreadReplyPositionsFromPostList(parameter)
    )
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

        if (prevIndex == index - 1 && crossThreadReplyRanges.size > 0) {
          val prevRange = crossThreadReplyRanges[crossThreadReplyRanges.lastIndex]
          crossThreadReplyRanges[crossThreadReplyRanges.lastIndex] = IntRange(prevRange.first, index)
        } else {
          crossThreadReplyRanges.add(IntRange(index, index))
        }

        prevIndex = index
        break
      }
    }

    return crossThreadReplyRanges
  }

  private fun extractReplyPositionsFromPostList(posts: List<Post>): List<IntRange> {
    if (!ChanSettings.markRepliesToYourPostOnScrollbar.get()) {
      return emptyList()
    }

    val savedPostNoSet: Set<Long> = HashSet(databaseSavedReplyManager.retainSavedPostNos(posts))
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

        if (prevIndex == index - 1 && replyRanges.size > 0) {
          val prevRange = replyRanges[replyRanges.lastIndex]
          replyRanges[replyRanges.lastIndex] = IntRange(prevRange.first, index)
        } else {
          replyRanges.add(IntRange(index, index))
        }

        prevIndex = index
        break
      }
    }

    return replyRanges
  }

}

data class PostMapInfoHolder(
  val replyPositionRanges: List<IntRange> = emptyList(),
  val crossThreadQuotePositionRanges: List<IntRange> = emptyList()
) {

  fun isTheSame(otherPostMapInfoHolder: PostMapInfoHolder): Boolean {
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