package com.github.k1rakishou.model.data.options

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class ChanLoadOptions(val chanLoadOption: ChanLoadOption) {

  fun canClearCache(): Boolean {
    return chanLoadOption is ChanLoadOption.ClearMemoryCache
  }

  fun isForceUpdating(postDescriptor: PostDescriptor?): Boolean {
    if (postDescriptor == null) {
      return chanLoadOption is ChanLoadOption.ForceUpdatePosts
    }

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

  /**
   * If [postDescriptors] is null then remove all posts
   * */
  class ForceUpdatePosts(val postDescriptors: Set<PostDescriptor>?) : ChanLoadOption() {
    override fun toString(): String {
      if (postDescriptors == null) {
        return "ForceUpdatePosts{AllPosts}"
      }

      return "ForceUpdatePosts{postDescriptorsCount=${postDescriptors.size}}"
    }
  }

}