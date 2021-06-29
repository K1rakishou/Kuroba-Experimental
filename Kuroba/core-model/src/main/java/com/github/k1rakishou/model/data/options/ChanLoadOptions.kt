package com.github.k1rakishou.model.data.options

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class ChanLoadOptions(val chanLoadOption: ChanLoadOption) {
  fun isNotDefault(): Boolean {
    return chanLoadOption !is ChanLoadOption.RetainAll
  }

  fun canClearCache(): Boolean {
    return chanLoadOption is ChanLoadOption.ClearMemoryCache
      || chanLoadOption is ChanLoadOption.ClearMemoryAndDatabaseCaches
      || chanLoadOption is ChanLoadOption.DeletePostsFromMemoryCache
  }

  fun canClearDatabase(): Boolean {
    return chanLoadOption is ChanLoadOption.ClearMemoryAndDatabaseCaches
  }

  fun isForceUpdating(postDescriptor: PostDescriptor): Boolean {
    return chanLoadOption is ChanLoadOption.ForceUpdatePosts
      && chanLoadOption.postDescriptors.contains(postDescriptor)
  }

  companion object {
    fun retainAll(): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.RetainAll)
    }

    fun clearMemoryCache(): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.ClearMemoryCache)
    }

    fun clearMemoryAndDatabaseCaches(): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.ClearMemoryAndDatabaseCaches)
    }

    fun deletePostFromMemoryCache(postDescriptor: PostDescriptor): ChanLoadOptions {
      return deletePostsFromMemoryCache(listOf(postDescriptor))
    }

    fun deletePostsFromMemoryCache(postDescriptors: Collection<PostDescriptor>): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.DeletePostsFromMemoryCache(postDescriptors))
    }

    // postsAreTheSame will always return false for posts in the set
    fun forceUpdatePosts(postDescriptors: Set<PostDescriptor>): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.ForceUpdatePosts(postDescriptors))
    }
  }
}

sealed class ChanLoadOption {
  object RetainAll : ChanLoadOption() {
    override fun toString(): String {
      return "RetainAll"
    }
  }

  object ClearMemoryCache : ChanLoadOption() {
    override fun toString(): String {
      return "ClearMemoryCache"
    }
  }

  object ClearMemoryAndDatabaseCaches : ChanLoadOption() {
    override fun toString(): String {
      return "ClearMemoryAndDatabaseCaches"
    }
  }

  class DeletePostsFromMemoryCache(val postDescriptors: Collection<PostDescriptor>) : ChanLoadOption() {
    override fun toString(): String {
      return "DeletePostsFromMemoryCache{postDescriptorsCount=${postDescriptors.size}}"
    }
  }

  class ForceUpdatePosts(val postDescriptors: Set<PostDescriptor>) : ChanLoadOption() {
    override fun toString(): String {
      return "ForceUpdatePosts{postDescriptorsCount=${postDescriptors.size}}"
    }
  }

}