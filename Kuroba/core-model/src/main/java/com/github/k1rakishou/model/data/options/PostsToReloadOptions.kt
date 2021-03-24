package com.github.k1rakishou.model.data.options

import com.github.k1rakishou.model.data.descriptor.PostDescriptor


sealed class PostsToReloadOptions {
  object ReloadAll : PostsToReloadOptions()
  data class Reload(val postDescriptors: Collection<PostDescriptor>) : PostsToReloadOptions()
}