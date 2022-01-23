package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.chan.core.manager.IPostFilterManager
import com.github.k1rakishou.chan.core.manager.IPostHideManager
import com.github.k1rakishou.common.toHashMapBy
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.data.post.PostComment
import com.github.k1rakishou.model.data.post.PostFilter
import com.github.k1rakishou.model.data.post.PostFilterResult
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class PostHideHelperTest {
  private val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create("test", "test")
  private val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(catalogDescriptor, 1L)

  private var postHideHelper = PostHideHelper(
    Mockito.mock(IPostHideManager::class.java),
    Mockito.mock(IPostFilterManager::class.java),
  )

  @Test
  fun simpleTest() {
    val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

    val resultMap = postHideHelper.processPostFiltersInternal(
      posts = listOf(createPost(postNo = 1)),
      chanDescriptor = threadDescriptor,
      hiddenPostsLookupMap = hiddenPosts(
        chanDescriptor = threadDescriptor,
        postNos = emptyList()
      ),
      postFilterMap = mapOf(),
      newChanPostHides = newChanPostHides
    )

    assertEquals(1, resultMap.size)
    assertEquals(0, newChanPostHides.size)
  }

  @Test
  fun shouldNotBeAbleToHideOrRemoveOriginalPostWhenInThread() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = ReplyChainBuilder()
        .post(createPost(postNo = 1))
        .childPosts {
          posts(
            createPost(postNo = 2),
            createPost(postNo = 3)
          )
        }
        .build()

      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(1),
          mapper = { chanPostHide -> chanPostHide.copy(onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf(),
        newChanPostHides = newChanPostHides
      )

      assertEquals(3, resultMap.size)
      assertEquals(0, newChanPostHides.size)
    }
  }

  @Test
  fun shouldNotBeAbleToHideOrRemoveThreadWhenInCatalogAndApplyToWholeThreadIsFalse() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = ReplyChainBuilder()
        .post(createPost(postNo = 1))
        .childPosts {
          posts(
            createPost(postNo = 2),
            createPost(postNo = 3)
          )
        }
        .build()

      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = catalogDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = catalogDescriptor,
          postNos = listOf(1),
          mapper = { chanPostHide -> chanPostHide.copy(applyToWholeThread = false, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf(),
        newChanPostHides = newChanPostHides
      )

      assertEquals(3, resultMap.size)
      assertEquals(0, newChanPostHides.size)
    }
  }

  @Test
  fun shouldBeAbleToHideOrRemoveOriginalPostWhenInCatalogAndApplyToWholeThreadIsTrue() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = mutableListOf<ChanPost>()
      posts += createPost(postNo = 1)
      posts += createPost(postNo = 2)
      posts += createPost(postNo = 3)

      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = catalogDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(1, 3),
          mapper = { chanPostHide -> chanPostHide.copy(applyToWholeThread = true, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf(),
        newChanPostHides = newChanPostHides
      )

      assertEquals(3, resultMap.size)
      assertEquals(0, newChanPostHides.size)

      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
    }
  }

  @Test
  fun shouldBeAbleToHideOrRemoveAllPostsInDiamondReplyChain() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = createReplyChain()
      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { chanPostHide -> chanPostHide.copy(applyToReplies = true, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf(),
        newChanPostHides = newChanPostHides
      )

      assertEquals(5, resultMap.size)
      assertEquals(3, newChanPostHides.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)
    }
  }

  @Test
  fun shouldNotBeAbleToRemoveChildPostsOfPostThatHasApplyToRepliesSetToFalseInDiamondReplyChain() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = createReplyChain()
      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { chanPostHide -> chanPostHide.copy(applyToReplies = false, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf(),
        newChanPostHides = newChanPostHides
      )

      assertEquals(5, resultMap.size)
      assertEquals(0, newChanPostHides.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)
    }
  }

  @Test
  fun shouldOnlyBeAbleToHideOrRemoveChildPostsIfParentPostHasManuallyRestoredSetToTrue() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = createReplyChain()
      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      // Step 1: hide the 2 post with all it's replies
      var resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { chanPostHide -> chanPostHide.copy(manuallyRestored = false, applyToReplies = true, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf(),
        newChanPostHides = newChanPostHides
      )

      // processPostFiltersInternal() will returns us a map of chanposthides it created
      // while processing replies. We will need to combine pass it into the next call of
      // processPostFiltersInternal()
      assertEquals(5, resultMap.size)
      assertEquals(3, newChanPostHides.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)

      // Step 2: unhide the 2 post and check that it's children are still hidden
      resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          newChanPostHides = newChanPostHides,
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { chanPostHide -> chanPostHide.copy(manuallyRestored = true, applyToReplies = true, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf(),
        newChanPostHides = mutableMapOf()
      )

      assertEquals(5, resultMap.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)
    }
  }

  @Test
  fun shouldHideOrRemoveOnlyOnePostByPostFilter() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = createReplyChain()
      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = mutableMapOf(),
        postFilterMap = postFilters(
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { postFilter ->
            postFilter.update(stub = onlyHide, remove = !onlyHide)
            postFilter
          }
        ),
        newChanPostHides = newChanPostHides
      )

      assertEquals(5, resultMap.size)
      assertEquals(1, newChanPostHides.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)
    }
  }

  @Test
  fun shouldHideOrRemovePostWithRepliesByPostFilter() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = createReplyChain()
      val newChanPostHides = mutableMapOf<PostDescriptor, PostHideHelper.ChanPostHideWrapper>()

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = mutableMapOf(),
        postFilterMap = postFilters(
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { postFilter ->
            postFilter.update(stub = onlyHide, remove = !onlyHide, replies = true)
            postFilter
          }
        ),
        newChanPostHides = newChanPostHides
      )

      assertEquals(5, resultMap.size)
      assertEquals(4, newChanPostHides.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)
    }
  }

  /**
   *                   post 3
   *                 /        \
   * post 1 - post 2            post 5
   *                 \        /
   *                   post 4
   *
   * Post 5 replies to posts 3 and 4.
   * Posts 3 and 4 reply to post 2.
   * Post 1 and 2 do not reply to any other posts.
   * */
  private fun createReplyChain(): List<ChanPost> {
    return ReplyChainBuilder()
      .post(createPost(postNo = 1))
      .post(createPost(postNo = 2))
      .childPosts {
        parentPosts(
          createPost(postNo = 3),
          createPost(postNo = 4)
        ) {
          post(createPost(postNo = 5))
        }
      }
      .build()
  }

  private fun hideRemoveTest(func: (PostFilterResult, Boolean) -> Unit) {
    func(PostFilterResult.Hide, true)
    func(PostFilterResult.Remove, false)
  }

  private fun postFilters(
    postNos: List<Long>,
    chanDescriptor: ChanDescriptor,
    mapper: ((PostFilter) -> PostFilter)? = null
  ): Map<PostDescriptor, PostFilter> {
    val resultMap = mutableMapOf<PostDescriptor, PostFilter>()

    if (postNos.isEmpty()) {
      return resultMap
    }

    postNos.forEach { postNo ->
      val postDescriptor = chanDescriptor.postDescriptor(postNo)
      val chanPostHide = PostFilter(ownerFilterId = null, filterEnabled = true)

      val updatedChanPostHide = mapper?.invoke(chanPostHide) ?: chanPostHide
      resultMap[postDescriptor] = updatedChanPostHide
    }

    return resultMap
  }

  private fun hiddenPosts(
    newChanPostHides: MutableMap<PostDescriptor, PostHideHelper.ChanPostHideWrapper> = mutableMapOf(),
    chanDescriptor: ChanDescriptor,
    postNos: List<Long>,
    mapper: ((ChanPostHide) -> ChanPostHide)? = null
  ): MutableMap<PostDescriptor, ChanPostHide> {
    val resultMap = mutableMapOf<PostDescriptor, ChanPostHide>()
    resultMap.putAll(newChanPostHides.entries.toHashMapBy(keySelector = { it.key }, valueSelector = { it.value.chanPostHide }))

    if (postNos.isEmpty()) {
      return resultMap
    }

    postNos.forEach { postNo ->
      val postDescriptor = chanDescriptor.postDescriptor(postNo)

      val chanPostHide = ChanPostHide(
        postDescriptor = postDescriptor,
        onlyHide = true,
        applyToWholeThread = false,
        applyToReplies = false,
        manuallyRestored = false
      )

      val updatedChanPostHide = mapper?.invoke(chanPostHide) ?: chanPostHide
      resultMap[postDescriptor] = updatedChanPostHide
    }

    return resultMap
  }

  private fun ChanDescriptor.postDescriptor(postNo: Long): PostDescriptor {
    return when (this) {
      is ChanDescriptor.CompositeCatalogDescriptor -> error("CompositeCatalogDescriptor")
      is ChanDescriptor.CatalogDescriptor -> PostDescriptor.create(this, postNo, postNo)
      is ChanDescriptor.ThreadDescriptor -> PostDescriptor.create(this, postNo)
    }
  }

  private fun createPost(postNo: Long): ChanPost {
    if (threadDescriptor.threadNo == postNo) {
      return ChanOriginalPost(
        chanPostId = 0L,
        postDescriptor = PostDescriptor.create(threadDescriptor, postNo),
        postImages = mutableListOf(),
        postIcons = mutableListOf<ChanPostHttpIcon>(),
        repliesTo = mutableSetOf(),
        timestamp = postNo,
        postComment = PostComment(originalComment = "$postNo", originalUnparsedComment = "$postNo", emptyList()),
        subject = null,
        tripcode = null,
        name = null,
        posterId = null,
        moderatorCapcode = null,
        isSavedReply = false,
        isSage = false,
        repliesFrom = mutableSetOf(),
        deleted = false,
        posterIdColor = 0,
        archived = false,
        closed = false,
        endless = false,
        sticky = false,
        lastModified = 0L
      )
    } else {
      return ChanPost(
        chanPostId = 0L,
        postDescriptor = PostDescriptor.Companion.create(threadDescriptor, postNo),
        _postImages = mutableListOf(),
        postIcons = mutableListOf<ChanPostHttpIcon>(),
        repliesTo = mutableSetOf(),
        timestamp = postNo,
        postComment = PostComment(originalComment = "$postNo", originalUnparsedComment = "$postNo", emptyList()),
        subject = null,
        tripcode = null,
        name = null,
        posterId = null,
        moderatorCapcode = null,
        isSavedReply = false,
        isSage = false,
        repliesFrom = mutableSetOf(),
        deleted = false,
        posterIdColor = 0,
      )
    }
  }

  class ReplyChainBuilder {
    val posts = mutableListOf<ChanPost>()
    val childPosts = mutableListOf<ChanPost>()

    fun post(chanPost: ChanPost): ReplyChainBuilder {
      posts += chanPost
      return this
    }

    fun posts(vararg chanPosts: ChanPost): ReplyChainBuilder {
      posts.addAll(chanPosts)
      return this
    }

    fun parentPosts(vararg parentPosts: ChanPost, builderFunc: ReplyChainBuilder.() -> Unit): ReplyChainBuilder {
      val builder = ReplyChainBuilder().also(builderFunc)

      parentPosts.forEach { parentPost ->
        parentPost.repliesFrom.addAll(builder.posts.map { it.postDescriptor })
        builder.posts.forEach { post -> post.repliesTo.add(parentPost.postDescriptor) }
      }

      posts.addAll(parentPosts)
      childPosts.addAll(builder.posts)
      childPosts.addAll(builder.childPosts)

      return this
    }

    fun childPosts(builderFunc: ReplyChainBuilder.() -> Unit): ReplyChainBuilder {
      val parentPost = posts.last()
      val builder = ReplyChainBuilder().also(builderFunc)

      parentPost.repliesFrom.addAll(builder.posts.map { it.postDescriptor })
      builder.posts.forEach { post -> post.repliesTo.add(parentPost.postDescriptor) }
      posts.addAll(builder.posts)
      posts.addAll(builder.childPosts)

      return this
    }

    fun build(): List<ChanPost> = posts + childPosts

  }

}