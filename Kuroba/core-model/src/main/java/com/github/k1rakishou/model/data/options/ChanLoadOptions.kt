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

  fun canForceUpdate(): Boolean {
    return chanLoadOption is ChanLoadOption.ForceUpdatePosts
  }

  fun canClearDatabase(): Boolean {
    return chanLoadOption is ChanLoadOption.ClearMemoryAndDatabaseCaches
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

    fun forceUpdatePosts(postDescriptors: Collection<PostDescriptor>): ChanLoadOptions {
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

  class ForceUpdatePosts(val postDescriptors: Collection<PostDescriptor>) : ChanLoadOption() {
    override fun toString(): String {
      return "ForceUpdatePosts{postDescriptorsCount=${postDescriptors.size}}"
    }
  }

}