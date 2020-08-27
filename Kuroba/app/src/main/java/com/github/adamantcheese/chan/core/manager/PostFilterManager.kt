package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.model.PostFilter
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class PostFilterManager {
  private val lock = ReentrantReadWriteLock()
  private val filterStorage = mutableMapWithCap<PostDescriptor, PostFilter>(512)

  fun insert(postDescriptor: PostDescriptor, postFilter: PostFilter) {
    lock.write { filterStorage[postDescriptor] = postFilter }
  }

  fun contains(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage.containsKey(postDescriptor) }
  }

  fun remove(postDescriptor: PostDescriptor) {
    lock.write { filterStorage.remove(postDescriptor) }
  }

  fun removeMany(postDescriptorList: List<PostDescriptor>) {
    lock.write {
      postDescriptorList.forEach { postDescriptor ->
        filterStorage.remove(postDescriptor)
      }
    }
  }

  fun update(postDescriptor: PostDescriptor, updateFunc: (PostFilter) -> Unit) {
    lock.write {
      val postFilter = filterStorage[postDescriptor] ?: PostFilter()
      updateFunc(postFilter)
      filterStorage[postDescriptor] = postFilter
    }
  }

  fun clear() {
    lock.write { filterStorage.clear() }
  }

  fun getFilterHighlightedColor(postDescriptor: PostDescriptor): Int {
    return lock.read { filterStorage[postDescriptor]?.filterHighlightedColor ?: 0 }
  }

  fun getFilterStub(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage[postDescriptor]?.filterStub ?: false }
  }

  fun getFilterRemove(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage[postDescriptor]?.filterRemove ?: false }
  }

  fun getFilterWatch(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage[postDescriptor]?.filterWatch ?: false }
  }

  fun getFilterReplies(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage[postDescriptor]?.filterReplies ?: false }
  }

  fun getFilterOnlyOP(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage[postDescriptor]?.filterOnlyOP ?: false }
  }

  fun getFilterSaved(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage[postDescriptor]?.filterSaved ?: false }
  }

  fun hasFilterParameters(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      return@read filterStorage[postDescriptor]?.filterSaved ?: false
        || filterStorage[postDescriptor]?.filterHighlightedColor != 0
        || filterStorage[postDescriptor]?.filterReplies ?: false
        || filterStorage[postDescriptor]?.filterStub ?: false
    }
  }

}