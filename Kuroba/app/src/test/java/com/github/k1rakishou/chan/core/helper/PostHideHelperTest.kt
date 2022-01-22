package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.chan.core.manager.IPostFilterManager
import com.github.k1rakishou.chan.core.manager.IPostHideManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.data.post.PostComment
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
    val resultMap = postHideHelper.processPostFiltersInternal(
      posts = listOf(createPost(postNo = 1)),
      chanDescriptor = threadDescriptor,
      hiddenPostsLookupMap = hiddenPosts(
        chanDescriptor = threadDescriptor,
        postNos = emptyList()
      ),
      postFilterMap = mapOf()
    )

    assertEquals(1, resultMap.size)
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

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(1),
          mapper = { chanPostHide -> chanPostHide.copy(onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf()
      )

      assertEquals(3, resultMap.size)
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

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = catalogDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = catalogDescriptor,
          postNos = listOf(1),
          mapper = { chanPostHide -> chanPostHide.copy(applyToWholeThread = false, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf()
      )

      assertEquals(3, resultMap.size)
    }
  }

  @Test
  fun shouldBeAbleToHideOrRemoveOriginalPostWhenInCatalogAndApplyToWholeThreadIsTrue() {
    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = mutableListOf<ChanPost>()
      posts += createPost(postNo = 1)
      posts += createPost(postNo = 2)
      posts += createPost(postNo = 3)

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = catalogDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(1, 3),
          mapper = { chanPostHide -> chanPostHide.copy(applyToWholeThread = true, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf()
      )

      assertEquals(3, resultMap.size)

      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
    }
  }

  @Test
  fun shouldBeAbleToHideOrRemoveAllPostsInDiamondReplyChain() {
    /**
     *                 child post 3
     *               /              \
     * parent post 2                 child post 5
     *               \              /
     *                 child post 4
     * */

    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = ReplyChainBuilder()
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

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { chanPostHide -> chanPostHide.copy(applyToWholeThread = true, applyToReplies = true, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf()
      )

      assertEquals(5, resultMap.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)
    }
  }

  @Test
  fun shouldNotBeAbleToRemoveChildPostsOfPostThatHasApplyToRepliesSetToFalseInDiamondReplyChain() {
    /**
     *                 child post 3
     *               /              \
     * parent post 2                 child post 5
     *               \              /
     *                 child post 4
     * */

    hideRemoveTest { postFilterResult, onlyHide ->
      val posts = ReplyChainBuilder()
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

      val resultMap = postHideHelper.processPostFiltersInternal(
        posts = posts,
        chanDescriptor = threadDescriptor,
        hiddenPostsLookupMap = hiddenPosts(
          chanDescriptor = threadDescriptor,
          postNos = listOf(2),
          mapper = { chanPostHide -> chanPostHide.copy(applyToWholeThread = true, applyToReplies = false, onlyHide = onlyHide) }
        ),
        postFilterMap = mapOf()
      )

      assertEquals(5, resultMap.size)

      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(1)]!!.postFilterResult)
      assertEquals(postFilterResult, resultMap[threadDescriptor.postDescriptor(2)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(3)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(4)]!!.postFilterResult)
      assertEquals(PostFilterResult.Leave, resultMap[threadDescriptor.postDescriptor(5)]!!.postFilterResult)
    }
  }

  private fun hideRemoveTest(func: (PostFilterResult, Boolean) -> Unit) {
    func(PostFilterResult.Hide, true)
    func(PostFilterResult.Remove, false)
  }

  private fun hiddenPosts(
    chanDescriptor: ChanDescriptor,
    postNos: List<Long>,
    mapper: ((ChanPostHide) -> ChanPostHide)? = null
  ): Map<PostDescriptor, ChanPostHide> {
    val resultMap = mutableMapOf<PostDescriptor, ChanPostHide>()

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