package com.github.k1rakishou.model.data.catalog

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ChanCatalog(
  val catalogDescriptor: ChanDescriptor.CatalogDescriptor,
  private val originalPosts: List<ChanOriginalPost>
) {
  private val lock = ReentrantReadWriteLock()
  private val postMap = mutableMapOf<PostDescriptor, ChanOriginalPost>()

  init {
    lock.write {
      postMap.clear()

      originalPosts.forEach { chanOriginalPost ->
        postMap[chanOriginalPost.postDescriptor] = chanOriginalPost
      }
    }
  }

  fun iteratePostsOrdered(iterator: (ChanOriginalPost) -> Unit) {
    iteratePostsOrderedWhile { chanOriginalPost ->
      iterator(chanOriginalPost)
      return@iteratePostsOrderedWhile true
    }
  }

  fun iteratePostsOrderedWhile(iterator: (ChanOriginalPost) -> Boolean) {
    lock.read {
      if (originalPosts.isEmpty()) {
        return@read
      }

      for (index in originalPosts.indices) {
        val chanPost = originalPosts.getOrNull(index)
          ?: return@read

        if (!iterator(chanPost)) {
          return@read
        }
      }
    }
  }

  fun <T> mapPostsOrdered(mapper: (ChanOriginalPost) -> T): List<T> {
    return lock.read {
      if (originalPosts.isEmpty()) {
        return@read emptyList()
      }

      val resultList = mutableListOf<T>()

      for (index in originalPosts.indices) {
        val chanPost = originalPosts.getOrNull(index)
          ?: return@read emptyList()

        resultList += mapper(chanPost)
      }

      return@read resultList
    }
  }

  fun findPostWithRepliesRecursive(
    postNo: Long,
    postsSet: MutableSet<ChanPost>
  ) {
    lock.read {
      for (post in originalPosts) {
        if (post.postNo() == postNo && !postsSet.contains(post)) {
          postsSet.add(post)

          post.iterateRepliesFrom { replyId ->
            findPostWithRepliesRecursive(replyId, postsSet)
          }
        }
      }
    }
  }

  fun getPost(postDescriptor: PostDescriptor): ChanOriginalPost? {
    return lock.read { postMap[postDescriptor] }
  }

}