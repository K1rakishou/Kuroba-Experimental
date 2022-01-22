package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostHide

interface IPostHideManager {
  fun createOrUpdateMany(chanPostHideList: Collection<ChanPostHide>)
  fun countPostHides(postDescriptors: List<PostDescriptor>): Int
  fun getHiddenPostsMap(postDescriptors: Set<PostDescriptor>): Map<PostDescriptor, ChanPostHide>
}