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
    postsSet: MutableSet<ChanPost>,
    includeRepliesFrom: Boolean,
    includeRepliesTo: Boolean,
    maxRecursion: Int = Int.MAX_VALUE
  ) {
    if (maxRecursion < 0) {
      return
    }

    require(includeRepliesFrom || includeRepliesTo) {
      "Either includeRepliesFrom or includeRepliesTo must be true"
    }

    val postsToCheck = mutableListOf<ChanPost>()

    lock.read {
      for (post in originalPosts) {
        if (post.postDescriptor != postDescriptor || postsSet.contains(post)) {
          continue
        }

        postsToCheck.add(post)
      }
    }

    for (post in postsToCheck) {
      if (postsSet.contains(post)) {
        continue
      }

      postsSet.add(post)

      if (includeRepliesFrom) {
        val repliesFrom = post.repliesFromCopy
        repliesFrom.forEach { lookUpPostDescriptor ->
          findPostWithRepliesRecursive(
            postDescriptor = lookUpPostDescriptor,
            postsSet = postsSet,
            includeRepliesFrom = includeRepliesFrom,
            includeRepliesTo = includeRepliesTo,
            maxRecursion = maxRecursion - 1
          )
        }
      }

      if (includeRepliesTo) {
        val repliesTo = post.repliesTo
        repliesTo.forEach { lookUpPostDescriptor ->
          findPostWithRepliesRecursive(
            postDescriptor = lookUpPostDescriptor,
            postsSet = postsSet,
            includeRepliesFrom = includeRepliesFrom,
            includeRepliesTo = includeRepliesTo,
            maxRecursion = maxRecursion - 1
          )
        }
      }
    }
  }

  fun getPost(postDescriptor: PostDescriptor): ChanOriginalPost? {
    return lock.read { postMap[postDescriptor] }
  }

}