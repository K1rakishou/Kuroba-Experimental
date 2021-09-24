package com.github.k1rakishou.chan.core.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CurrentlyDisplayedCatalogPostsRepository {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val _catalogPosts = mutableListWithCap<PostDescriptor>(128)
  val catalogPosts: List<PostDescriptor>
    get() = lock.read { _catalogPosts }

  fun updatePosts(postDescriptors: List<PostDescriptor>) {
    lock.write {
      _catalogPosts.clear()
      _catalogPosts.addAll(postDescriptors)
    }
  }

  fun iteratePosts(iterator: (PostDescriptor) -> Unit) {
    lock.read {
      catalogPosts.forEach(iterator)
    }
  }

  fun getPosts(): List<PostDescriptor> {
    return lock.read { catalogPosts }
  }

  fun clear() {
    lock.write { _catalogPosts.clear() }
  }

}