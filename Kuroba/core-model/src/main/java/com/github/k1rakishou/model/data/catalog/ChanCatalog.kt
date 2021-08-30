package com.github.k1rakishou.model.data.catalog

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ChanCatalog(
  val catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
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

  fun postsCount(): Int {
    return lock.read { originalPosts.size }
  }

  fun isEmpty(): Boolean {
    return lock.read { originalPosts.isEmpty() }
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
    postDescriptor: PostDescriptor,
    postsSet: MutableSet<ChanPost>
  ): Boolean {
    return lock.read {
      var found = false

      for (post in originalPosts) {
        if (post.postDescriptor != postDescriptor || postsSet.contains(post)) {
          continue
        }

        found = true
        postsSet.add(post)

        post.iterateRepliesFrom { replyId ->
          val lookUpPostDescriptor = PostDescriptor.create(
            post.postDescriptor.descriptor,
            replyId
          )

          findPostWithRepliesRecursive(lookUpPostDescriptor, postsSet)
        }
      }

      return@read found
    }
  }

  fun getPost(postDescriptor: PostDescriptor): ChanOriginalPost? {
    return lock.read { postMap[postDescriptor] }
  }

}