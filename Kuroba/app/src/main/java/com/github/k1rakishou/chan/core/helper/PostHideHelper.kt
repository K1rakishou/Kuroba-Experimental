package com.github.k1rakishou.chan.core.helper

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.chan.core.manager.IPostFilterManager
import com.github.k1rakishou.chan.core.manager.IPostHideManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableIteration
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
    posts: List<ChanPost>
  ): ModularResult<List<ChanPost>> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val postDescriptorSet = posts.map { post -> post.postDescriptor }.toSet()
        val postFilterMap = postFilterManager.getManyPostFilters(postDescriptorSet)
        val hiddenPostsLookupMap = postHideManager.getHiddenPostsMap(postDescriptorSet)

        val resultMap = processPostFiltersInternal(
          posts = posts,
          chanDescriptor = chanDescriptor,
          hiddenPostsLookupMap = hiddenPostsLookupMap,
          postFilterMap = postFilterMap
        )

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
    hiddenPostsLookupMap: Map<PostDescriptor, ChanPostHide>,
    postFilterMap: Map<PostDescriptor, PostFilter>
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

      resultMap[postDescriptor] = ChanPostWithFilterResult(
        chanPost = post,
        postFilterResult = postFilterResult
      )
    }

    if (!processingCatalog) {
      val newChanPostHides = mutableMapOf<PostDescriptor, ChanPostHide>()

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
            postHideMap = hiddenPostsLookupMap,
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

          if (newChanPostHides[sourcePostDescriptor]?.manuallyRestored != true) {
            newChanPostHides[sourcePostDescriptor] = ChanPostHide(
              postDescriptor = sourcePostDescriptor,
              onlyHide = onlyHide,
              applyToWholeThread = false,
              applyToReplies = applyToReplies,
              manuallyRestored = false
            )
          }

          sourceChanPostWithFilterResult.postFilterResult = targetChanPostWithFilterResult.postFilterResult
          break
        }
      }

      if (newChanPostHides.isNotEmpty()) {
        postHideManager.createOrUpdateMany(newChanPostHides.values)
      }
    }

    return resultMap
  }

  private fun findParentNonNullPostHide(
    postDescriptor: PostDescriptor,
    postHideMap: Map<PostDescriptor, ChanPostHide>,
    postMap: Map<PostDescriptor, ChanPost>
  ): ChanPostHide? {
    val chanPostHide = postHideMap[postDescriptor]
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
        postHideMap = postHideMap,
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

    val attemptingToRemove = (postFilter?.remove == true) || (postHide?.onlyHide == false)
    if (!attemptingToRemove) {
      return false
    }

    if (post.isOP()) {
      if (processingCatalog) {
        return postHide?.applyToWholeThread == true
      }

      return false
    }

    if (postHide != null) {
      if (postHide.manuallyRestored) {
        return false
      }

      return true
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

    val attemptingToHide = (postFilter?.stub == true) || (postHide?.onlyHide == true)
    if (!attemptingToHide) {
      return false
    }

    if (post.isOP()) {
      if (processingCatalog) {
        return postHide?.applyToWholeThread == true
      }

      return false
    }

    if (postHide != null) {
      if (postHide.manuallyRestored) {
        return false
      }

      return true
    }

    return false
  }

  companion object {
    private const val TAG = "PostHideHelper"
  }
}