package com.github.k1rakishou.chan.ui.adapter

import com.github.k1rakishou.chan.core.helper.ChanLoadProgressEvent
import com.github.k1rakishou.chan.core.helper.ChanLoadProgressNotifier
import com.github.k1rakishou.chan.core.helper.PostHideHelper
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed
import com.github.k1rakishou.v2.KurobaSettings
import java.util.Collections

class PostsFilter(
  private val chanLoadProgressNotifier: ChanLoadProgressNotifier,
  private val postHideHelper: PostHideHelper,
  private val catalogSortingOrder: CatalogSortingOrder
) {

  suspend fun applyFilter(
    chanDescriptor: ChanDescriptor,
    posts: MutableList<ChanPost>,
    additionalPostsToReparse: MutableSet<PostDescriptor>
  ): List<PostIndexed> {
    chanLoadProgressNotifier.sendProgressEvent(
      ChanLoadProgressEvent.ApplyingFilters(
        chanDescriptor = chanDescriptor,
        postHidesCount = postHideHelper.countPostHides(posts),
        postFiltersCount = postHideHelper.countMatchedFilters(posts)
      )
    )

    if (catalogSortingOrder != CatalogSortingOrder.BUMP && chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
      processOrder(catalogSortingOrder, posts as MutableList<ChanOriginalPost>)
    }

    // Process hidden by filter and post/thread hiding
    val retainedPosts = postHideHelper.processPostFilters(chanDescriptor, posts, additionalPostsToReparse)
      .safeUnwrap { error ->
        Logger.e(TAG, "postHideHelper.filterHiddenPosts error", error)
        return emptyList()
      }

    val indexedPosts = mutableListWithCap<PostIndexed>(retainedPosts.size)

    for ((index, retainedPost) in retainedPosts.withIndex()) {
      indexedPosts.add(PostIndexed(retainedPost, index))
    }

    return indexedPosts
  }

  enum class CatalogSortingOrder(val orderName: String) {
    BUMP("bump"),
    REPLY("reply"),
    IMAGE("image"),
    NEWEST("newest"),
    OLDEST("oldest"),
    MODIFIED("modified"),
    ACTIVITY("activity");

    val isBump: Boolean
      get() = this == BUMP

    companion object {
      fun current(kurobaSettings: KurobaSettings): CatalogSortingOrder {
        val borderOrderName = kurobaSettings.application.boardOrder.readBlocking()

        return entries.firstOrNull { it.orderName == borderOrderName }
          ?: BUMP
      }
    }
  }

  companion object {
    private const val TAG = "PostsFilter"

    fun processOrder(catalogSortingOrder: CatalogSortingOrder, posts: List<ChanOriginalPost>) {
      when (catalogSortingOrder) {
        CatalogSortingOrder.IMAGE -> Collections.sort(posts, IMAGE_COMPARATOR)
        CatalogSortingOrder.REPLY -> Collections.sort(posts, REPLY_COMPARATOR)
        CatalogSortingOrder.NEWEST -> Collections.sort(posts, NEWEST_COMPARATOR)
        CatalogSortingOrder.OLDEST -> Collections.sort(posts, OLDEST_COMPARATOR)
        CatalogSortingOrder.MODIFIED -> Collections.sort(posts, MODIFIED_COMPARATOR)
        CatalogSortingOrder.ACTIVITY -> Collections.sort(posts, THREAD_ACTIVITY_COMPARATOR)
        CatalogSortingOrder.BUMP -> {
          // no-op
        }
      }
    }

    private val IMAGE_COMPARATOR = Comparator<ChanOriginalPost> { lhs, rhs ->
      rhs.catalogImagesCount - lhs.catalogImagesCount
    }

    private val REPLY_COMPARATOR = Comparator<ChanOriginalPost> { lhs, rhs ->
      rhs.catalogRepliesCount - lhs.catalogRepliesCount
    }

    private val NEWEST_COMPARATOR = Comparator<ChanOriginalPost> { lhs, rhs ->
      (rhs.timestamp - lhs.timestamp).toInt()
    }

    private val OLDEST_COMPARATOR = Comparator<ChanOriginalPost> { lhs, rhs ->
      (lhs.timestamp - rhs.timestamp).toInt()
    }

    private val MODIFIED_COMPARATOR = Comparator<ChanOriginalPost> { lhs, rhs ->
      (rhs.lastModified - lhs.lastModified).toInt()
    }

    private val THREAD_ACTIVITY_COMPARATOR = Comparator<ChanOriginalPost> { lhs, rhs ->
      val currentTimeSeconds = System.currentTimeMillis() / 1000
      // we can't divide by zero, but we can divide by the smallest thing that's closest to 0 instead
      val eps = 0.0001f

      val lhsDivider = if (lhs.catalogRepliesCount > 0) {
        lhs.catalogRepliesCount.toFloat()
      } else {
        eps
      }

      val rhsDivider = if (rhs.catalogRepliesCount > 0) {
        rhs.catalogRepliesCount.toFloat()
      } else {
        eps
      }

      val score1 = ((currentTimeSeconds - lhs.timestamp).toFloat() / lhsDivider).toLong()
      val score2 = ((currentTimeSeconds - rhs.timestamp).toFloat() / rhsDivider).toLong()

      return@Comparator score1.compareTo(score2)
    }
  }

}