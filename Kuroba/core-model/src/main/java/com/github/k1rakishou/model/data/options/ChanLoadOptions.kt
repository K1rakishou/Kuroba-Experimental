package com.github.k1rakishou.model.data.options

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class ChanLoadOptions(val chanLoadOption: ChanLoadOption) {
  fun isNotDefault(): Boolean {
    return chanLoadOption !is ChanLoadOption.RetainAll
  }

  fun canClearCache(): Boolean {
    return chanLoadOption is ChanLoadOption.ClearMemoryCache
      || chanLoadOption is ChanLoadOption.ClearMemoryAndDatabaseCaches
  }

  fun canClearDatabase(): Boolean {
    return chanLoadOption is ChanLoadOption.ClearMemoryAndDatabaseCaches
  }

  fun isForceUpdating(postDescriptor: PostDescriptor): Boolean {
    return chanLoadOption is ChanLoadOption.ForceUpdatePosts
      && (chanLoadOption.postDescriptors == null || chanLoadOption.postDescriptors.contains(postDescriptor))
  }

  companion object {
    fun retainAll(): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.RetainAll)
    }

    fun clearMemoryCache(): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.ClearMemoryCache)
    }

    // postsAreTheSame will always return false for posts in the set
    fun forceUpdatePosts(postDescriptors: Set<PostDescriptor>): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.ForceUpdatePosts(postDescriptors))
    }

    fun forceUpdateAllPosts(): ChanLoadOptions {
      return ChanLoadOptions(ChanLoadOption.ForceUpdatePosts(null))
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

  class ForceUpdatePosts(val postDescriptors: Set<PostDescriptor>?) : ChanLoadOption() {
    override fun toString(): String {
      if (postDescriptors == null) {
        return "ForceUpdatePosts{AllPosts}"
      }

      return "ForceUpdatePosts{postDescriptorsCount=${postDescriptors.size}}"
    }
  }

}