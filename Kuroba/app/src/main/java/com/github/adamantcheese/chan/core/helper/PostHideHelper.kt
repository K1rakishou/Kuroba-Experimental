package com.github.adamantcheese.chan.core.helper

import android.annotation.SuppressLint
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.manager.PostHideManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostFilter
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils.findPostWithReplies
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.post.ChanPostHide
import java.util.*

class PostHideHelper(
  private val postHideManager: PostHideManager,
  private val postFilterManager: PostFilterManager
) {

  /**
   * Searches for hidden posts in the PostHide table then checks whether there are posts with a reply
   * to already hidden posts and if there are hides them as well.
   */
  suspend fun filterHiddenPosts(posts: List<Post>): ModularResult<List<Post>> {
    return ModularResult.Try {
      val postDescriptorList = posts.map { post -> post.postDescriptor }.toSet()

      @SuppressLint("UseSparseArrays")
      val postsFastLookupMap: MutableMap<Long, Post> = LinkedHashMap()
      for (post in posts) {
        postsFastLookupMap[post.no] = post
      }

      applyFiltersToReplies(posts, postsFastLookupMap)
      val hiddenPostsLookupMap = postHideManager.getHiddenPostsMap(postDescriptorList)

      // find replies to hidden posts and add them to the PostHide table in the database
      // and to the hiddenPostsLookupMap
      hideRepliesToAlreadyHiddenPosts(postsFastLookupMap, hiddenPostsLookupMap)
      val resultList: MutableList<Post> = ArrayList()

      // filter out hidden posts
      for (post in postsFastLookupMap.values) {
        if (postFilterManager.getFilterRemove(post.postDescriptor)) {
          // this post is already filtered by some custom filter
          continue
        }

        val hiddenPost = findHiddenPost(hiddenPostsLookupMap, post)
        if (hiddenPost != null) {
          if (hiddenPost.onlyHide) {
            // hide post
            updatePostWithCustomFilter(
              childPost = post,
              filterHighlightedColor = 0,
              filterStub = true,
              filterRemove = false,
              filterWatch = false,
              filterReplies = hiddenPost.applyToReplies,
              filterSaved = false
            )

            resultList.add(post)
          } else {
            // remove post
            if (post.isOP) {
              // hide OP post only if the user hid the whole thread
              if (!hiddenPost.applyToWholeThread) {
                resultList.add(post)
              }
            }
          }

          continue
        }

        // no record of hidden post in the DB
        resultList.add(post)
      }

      return@Try resultList
    }
  }

  private suspend fun hideRepliesToAlreadyHiddenPosts(
    postsFastLookupMap: Map<Long, Post>,
    hiddenPostsLookupMap: MutableMap<Long, ChanPostHide>
  ) {
    val newHiddenPosts: MutableList<ChanPostHide> = ArrayList()

    for (post in postsFastLookupMap.values) {
      if (hiddenPostsLookupMap.containsKey(post.no)) {
        continue
      }

      for (replyNo in post.repliesTo) {
        if (hiddenPostsLookupMap.containsKey(replyNo)) {
          val parentPost = postsFastLookupMap[replyNo]
            ?: continue
          val parentHiddenPost = hiddenPostsLookupMap[replyNo]
            ?: continue

          val filterRemove: Boolean = postFilterManager.getFilterRemove(
            parentPost.postDescriptor
          )

          if (!filterRemove || !parentHiddenPost.applyToReplies) {
            continue
          }

          val newHiddenPost = ChanPostHide(
            postDescriptor = post.postDescriptor,
            onlyHide = false,
            applyToWholeThread = parentHiddenPost.onlyHide,
            applyToReplies = true
          )

          // TODO(KurobaEx): archive ghost posts
          hiddenPostsLookupMap[newHiddenPost.postDescriptor.postNo] = newHiddenPost
          newHiddenPosts.add(newHiddenPost)

          //post is already hidden no need to check other replies
          break
        }
      }
    }

    if (newHiddenPosts.isEmpty()) {
      return
    }

    postHideManager.createMany(newHiddenPosts)
  }

  private fun applyFiltersToReplies(posts: List<Post>, postsFastLookupMap: MutableMap<Long, Post>) {
    for (post in posts) {
      if (post.isOP) {
        // skip the OP
        continue
      }

      if (!postFilterManager.hasFilterParameters(post.postDescriptor)) {
        continue
      }

      val filterRemove: Boolean = postFilterManager.getFilterRemove(post.postDescriptor)
      val filterStub: Boolean = postFilterManager.getFilterStub(post.postDescriptor)

      if (filterRemove && filterStub) {
        Logger.e(TAG, "Post has both filterRemove and filterStub flags")
        continue
      }

      applyPostFilterActionToChildPosts(post, postsFastLookupMap)
    }
  }

  /**
   * Takes filter parameters from the post and assigns them to all posts in the current reply chain.
   * If some post already has another filter's parameters - does not overwrite them.
   * Returns a chain of hidden posts.
   */
  private fun applyPostFilterActionToChildPosts(parentPost: Post, postsFastLookupMap: MutableMap<Long, Post>) {
    if (postsFastLookupMap.isEmpty()
      || !postFilterManager.getFilterReplies(parentPost.postDescriptor)) {
      // do nothing with replies if filtering is disabled for replies
      return
    }

    // find all replies to the post recursively
    val postWithAllReplies = findPostWithReplies(parentPost.no, ArrayList(postsFastLookupMap.values))
    val postNoWithAllReplies: MutableSet<Long> = HashSet(postWithAllReplies.size)
    for (p in postWithAllReplies) {
      postNoWithAllReplies.add(p.no)
    }
    for (no in postNoWithAllReplies) {
      if (no == parentPost.no) {
        // do nothing with the parent post
        continue
      }

      val childPost = postsFastLookupMap[no]
      // cross-thread post
        ?: continue

      val hasFilterParameters = postFilterManager.hasFilterParameters(childPost.postDescriptor)
      if (hasFilterParameters) {
        // do not overwrite filter parameters from another filter
        continue
      }

      updatePostWithCustomFilter(
        childPost = childPost,
        filterHighlightedColor = postFilterManager.getFilterHighlightedColor(parentPost.postDescriptor),
        filterStub = postFilterManager.getFilterStub(parentPost.postDescriptor),
        filterRemove = postFilterManager.getFilterRemove(parentPost.postDescriptor),
        filterWatch = postFilterManager.getFilterWatch(parentPost.postDescriptor),
        filterReplies = true,
        filterSaved = postFilterManager.getFilterSaved(parentPost.postDescriptor)
      )

      // assign the filter parameters to the child post
      postsFastLookupMap[no] = childPost
      postWithAllReplies.remove(childPost)
      postWithAllReplies.add(childPost)
    }
  }

  /**
   * Rebuilds a child post with custom filter parameters
   */
  private fun updatePostWithCustomFilter(
    childPost: Post,
    filterHighlightedColor: Int,
    filterStub: Boolean,
    filterRemove: Boolean,
    filterWatch: Boolean,
    filterReplies: Boolean,
    filterSaved: Boolean
  ) {
    postFilterManager.update(childPost.postDescriptor) { postFilter: PostFilter ->
      postFilter.enabled = true
      postFilter.filterHighlightedColor = filterHighlightedColor
      postFilter.filterStub = filterStub
      postFilter.filterRemove = filterRemove
      postFilter.filterWatch = filterWatch
      postFilter.filterReplies = filterReplies
      postFilter.filterSaved = filterSaved
    }
  }

  private fun findHiddenPost(
    hiddenPostsLookupMap: Map<Long, ChanPostHide>,
    post: Post
  ): ChanPostHide? {
    if (hiddenPostsLookupMap.isEmpty()) {
      return null
    }

    return hiddenPostsLookupMap[post.no]
  }

  companion object {
    private const val TAG = "PostHideHelper"
  }
}