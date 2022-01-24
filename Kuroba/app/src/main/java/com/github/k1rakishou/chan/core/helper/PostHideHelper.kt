package com.github.k1rakishou.chan.core.helper

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.chan.core.manager.IPostFilterManager
import com.github.k1rakishou.chan.core.manager.IPostHideManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.data.post.ChanPostWithFilterResult
import com.github.k1rakishou.model.data.post.PostFilter
import com.github.k1rakishou.model.data.post.PostFilterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class PostHideHelper(
  private val postHideManager: IPostHideManager,
  private val postFilterManager: IPostFilterManager
) {

  fun countPostHides(posts: List<ChanPost>): Int {
    return postHideManager.countPostHides(posts.map { it.postDescriptor })
  }

  fun countMatchedFilters(posts: List<ChanPost>): Int {
    return postFilterManager.countMatchedFilters(posts.map { it.postDescriptor })
  }

  /**
   * Searches for hidden posts in the PostHide table then checks whether there are posts with a reply
   * to already hidden posts and if there are hides them as well.
   */
  suspend fun processPostFilters(
    chanDescriptor: ChanDescriptor,
    posts: List<ChanPost>,
    additionalPostsToReparse: MutableSet<PostDescriptor>
  ): ModularResult<List<ChanPost>> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val postDescriptorSet = posts.map { post -> post.postDescriptor }.toSet()
        val postFilterMap = postFilterManager.getManyPostFilters(postDescriptorSet)
        val hiddenPostsLookupMap = postHideManager.getHiddenPostsMap(postDescriptorSet).toMutableMap()
        val newChanPostHides = mutableMapOf<PostDescriptor, ChanPostHideWrapper>()

        Logger.d(TAG, "processPostFilters($chanDescriptor) start")

        val resultMap = processPostFiltersInternal(
          posts = posts,
          chanDescriptor = chanDescriptor,
          hiddenPostsLookupMap = hiddenPostsLookupMap,
          postFilterMap = postFilterMap,
          newChanPostHides = newChanPostHides
        )

        if (newChanPostHides.isNotEmpty()) {
          val chanPostHides = newChanPostHides.values.map { it.chanPostHide }
          postHideManager.createOrUpdateMany(chanPostHides)

          val postDescriptors = newChanPostHides.values.mapNotNull { chanPostHideWrapper ->
            if (!chanPostHideWrapper.createdByFilter) {
              return@mapNotNull null
            }

            return@mapNotNull chanPostHideWrapper.chanPostHide.postDescriptor
          }.toSet()

          if (postDescriptors.isNotEmpty()) {
            additionalPostsToReparse.addAll(postDescriptors)
          }
        }

        var hiddenPostsCount = 0
        var removedPostsCount = 0
        var normalPostsCount = 0

        for ((_, chanPostWithFilterResult) in resultMap.entries) {
          when (chanPostWithFilterResult.postFilterResult) {
            PostFilterResult.Hide -> ++hiddenPostsCount
            PostFilterResult.Remove -> ++removedPostsCount
            PostFilterResult.Leave -> ++normalPostsCount
          }
        }

        Logger.d(TAG, "processPostFilters($chanDescriptor) end (hiddenPostsCount=$hiddenPostsCount, " +
          "removedPostsCount=$removedPostsCount, normalPostsCount=$normalPostsCount, total=${resultMap.size})")

        resultMap.mutableIteration { mutableIterator, entry ->
          val chanPostWithFilterResult = entry.value
          if (chanPostWithFilterResult.postFilterResult == PostFilterResult.Remove) {
            mutableIterator.remove()
          }

          return@mutableIteration true
        }

        if (AppModuleAndroidUtils.isDevBuild()) {
          resultMap.values.forEach { chanPostWithFilterResult ->
            val postDescriptor = chanPostWithFilterResult.chanPost.postDescriptor
            val postFilterResult = chanPostWithFilterResult.postFilterResult

            if (postFilterResult == PostFilterResult.Remove) {
              error("Post with PostFilterResult.Remove found! postDescriptor=${postDescriptor}")
            }
          }
        }

        return@Try resultMap.values.map { it.chanPost }
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun processPostFiltersInternal(
    posts: List<ChanPost>,
    chanDescriptor: ChanDescriptor,
    hiddenPostsLookupMap: MutableMap<PostDescriptor, ChanPostHide>,
    postFilterMap: Map<PostDescriptor, PostFilter>,
    newChanPostHides: MutableMap<PostDescriptor, ChanPostHideWrapper>,
  ): MutableMap<PostDescriptor, ChanPostWithFilterResult> {
    val resultMap = linkedMapWithCap<PostDescriptor, ChanPostWithFilterResult>(posts.size)
    val processingCatalog = chanDescriptor is ChanDescriptor.ICatalogDescriptor

    val postsFastLookupMap = linkedMapWithCap<PostDescriptor, ChanPost>(posts.size)
    for (post in posts) {
      postsFastLookupMap[post.postDescriptor] = post
    }

    // First pass, process the posts
    for (post in posts) {
      val postDescriptor = post.postDescriptor
      val postHide = hiddenPostsLookupMap[postDescriptor]
      val postFilter = postFilterMap[postDescriptor]

      if (postFilter != null) {
        check(postFilter.enabled) { "Post filter must be enabled here" }
      }

      val canHideThisPost = canHidePost(processingCatalog, post, postFilter, postHide)
      val canRemoveThisPost = canRemovePost(processingCatalog, post, postFilter, postHide)

      val postFilterResult = when {
        canRemoveThisPost -> PostFilterResult.Remove
        canHideThisPost -> PostFilterResult.Hide
        else -> PostFilterResult.Leave
      }

      if ((canHideThisPost || canRemoveThisPost) && postHide == null && postFilter != null) {
        @Suppress("RedundantIf") val onlyHide = if ((canHideThisPost && canRemoveThisPost) || canRemoveThisPost) {
          false
        } else {
          true
        }

        createNewChanPostHide(
          postFilter = postFilter,
          postDescriptor = postDescriptor,
          newChanPostHides = newChanPostHides,
          hiddenPostsLookupMap = hiddenPostsLookupMap,
          onlyHide = onlyHide,
          applyToReplies = postFilter.replies
        )
      }

      resultMap[postDescriptor] = ChanPostWithFilterResult(
        chanPost = post,
        postFilterResult = postFilterResult
      )
    }

    if (!processingCatalog) {
      // Second pass, process the reply chains (Do not do this in the catalogs)
      for ((sourcePost, _) in resultMap.values) {
        val sourcePostDescriptor = sourcePost.postDescriptor

        val sourceChanPostWithFilterResult = resultMap[sourcePostDescriptor] ?: continue
        if (sourceChanPostWithFilterResult.postFilterResult != PostFilterResult.Leave) {
          // Already processed and we either hide or remove it, no need to process it again
          continue
        }

        val sourcePostHide = hiddenPostsLookupMap[sourcePostDescriptor]
        if (sourcePostHide?.manuallyRestored == true) {
          // This post was manually unhidden/unremoved by the user. Do not auto hide/remove it again.
          sourceChanPostWithFilterResult.postFilterResult = PostFilterResult.Leave
          continue
        }

        for (targetPostDescriptor in sourcePost.repliesTo) {
          val targetPostHide = findParentNonNullPostHide(
            postDescriptor = targetPostDescriptor,
            hiddenPostsLookupMap = hiddenPostsLookupMap,
            newChanPostHides = newChanPostHides,
            postMap = postsFastLookupMap
          )

          var targetPostFilter = postFilterMap[targetPostDescriptor]
          if (targetPostFilter == null && targetPostHide != null) {
            targetPostFilter = postFilterMap[targetPostHide.postDescriptor]
          }

          val applyToReplies = processingCatalog
            || (targetPostFilter?.replies == true)
            || (targetPostHide?.applyToReplies == true)

          if (!applyToReplies) {
            continue
          }

          val targetChanPostWithFilterResult = resultMap[targetPostDescriptor]
            ?: continue

          if (targetChanPostWithFilterResult.postFilterResult == PostFilterResult.Leave) {
            continue
          }

          val onlyHide = targetChanPostWithFilterResult.postFilterResult == PostFilterResult.Hide

          createNewChanPostHide(
            postFilter = targetPostFilter,
            postDescriptor = sourcePostDescriptor,
            newChanPostHides = newChanPostHides,
            hiddenPostsLookupMap = hiddenPostsLookupMap,
            onlyHide = onlyHide,
            applyToReplies = applyToReplies
          )

          sourceChanPostWithFilterResult.postFilterResult = targetChanPostWithFilterResult.postFilterResult
          break
        }
      }
    }

    return resultMap
  }

  private fun createNewChanPostHide(
    postFilter: PostFilter?,
    postDescriptor: PostDescriptor,
    newChanPostHides: MutableMap<PostDescriptor, ChanPostHideWrapper>,
    hiddenPostsLookupMap: MutableMap<PostDescriptor, ChanPostHide>,
    onlyHide: Boolean,
    applyToReplies: Boolean,
  ) {
    if (newChanPostHides[postDescriptor]?.chanPostHide?.manuallyRestored == true) {
      return
    }

    if (newChanPostHides.containsKey(postDescriptor)) {
      return
    }

    val chanPostHide = ChanPostHide(
      postDescriptor = postDescriptor,
      onlyHide = onlyHide,
      applyToWholeThread = false,
      applyToReplies = applyToReplies,
      manuallyRestored = false
    )

    newChanPostHides[postDescriptor] = ChanPostHideWrapper(
      chanPostHide = chanPostHide,
      createdByFilter = postFilter != null
    )
    hiddenPostsLookupMap[postDescriptor] = chanPostHide
  }

  private fun findParentNonNullPostHide(
    postDescriptor: PostDescriptor,
    hiddenPostsLookupMap: Map<PostDescriptor, ChanPostHide>,
    newChanPostHides: Map<PostDescriptor, ChanPostHideWrapper>,
    postMap: Map<PostDescriptor, ChanPost>
  ): ChanPostHide? {
    var chanPostHide = hiddenPostsLookupMap[postDescriptor]
    if (chanPostHide != null) {
      return chanPostHide
    }

    chanPostHide = newChanPostHides[postDescriptor]?.chanPostHide
    if (chanPostHide != null) {
      return chanPostHide
    }

    val chanPost = postMap[postDescriptor]
    if (chanPost == null) {
      return null
    }

    for (targetPostDescriptor in chanPost.repliesTo) {
      val parentChanPostHide = findParentNonNullPostHide(
        postDescriptor = targetPostDescriptor,
        hiddenPostsLookupMap = hiddenPostsLookupMap,
        newChanPostHides = newChanPostHides,
        postMap = postMap
      )

      if (parentChanPostHide != null) {
        return parentChanPostHide
      }
    }

    return null
  }

  private fun canRemovePost(
    processingCatalog: Boolean,
    post: ChanPost,
    postFilter: PostFilter?,
    postHide: ChanPostHide?
  ): Boolean {
    if (postFilter == null && postHide == null) {
      return false
    }

    if (postFilter != null) {
      val attemptingToHide = (postFilter.enabled && postFilter.remove)
      if (attemptingToHide) {
        return true
      }
    }

    if (postHide != null) {
      if (postHide.manuallyRestored) {
        return false
      }

      if (processingCatalog) {
        if (post.isOP() && !postHide.applyToWholeThread) {
          return false
        }
      } else {
        if (post.isOP()) {
          return false
        }
      }

      if (!postHide.onlyHide) {
        return true
      }
    }

    return false
  }

  private fun canHidePost(
    processingCatalog: Boolean,
    post: ChanPost,
    postFilter: PostFilter?,
    postHide: ChanPostHide?
  ): Boolean {
    if (postFilter == null && postHide == null) {
      return false
    }

    if (postFilter != null) {
      val attemptingToHide = (postFilter.enabled && postFilter.stub)
      if (attemptingToHide) {
        return true
      }
    }

    if (postHide != null) {
      if (postHide.manuallyRestored) {
        return false
      }

      if (processingCatalog) {
        if (post.isOP() && !postHide.applyToWholeThread) {
          return false
        }
      } else {
        if (post.isOP()) {
          return false
        }
      }

      if (postHide.onlyHide) {
        return true
      }
    }

    return false
  }

  class ChanPostHideWrapper(
    val chanPostHide: ChanPostHide,
    val createdByFilter: Boolean
  )

  companion object {
    private const val TAG = "PostHideHelper"
  }
}