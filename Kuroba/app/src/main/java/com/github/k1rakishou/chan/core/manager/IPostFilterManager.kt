package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.PostFilter

interface IPostFilterManager {
  fun countMatchedFilters(postDescriptors: List<PostDescriptor>): Int
  fun getManyPostFilters(postDescriptors: Collection<PostDescriptor>): Map<PostDescriptor, PostFilter>
}