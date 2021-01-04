package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.post.ChanPostImage

class FilterOutHiddenImagesUseCase(
  private val postHideManager: PostHideManager,
  private val postFilterManager: PostFilterManager
) : IUseCase<List<ChanPostImage>, List<ChanPostImage>> {

  override fun execute(parameter: List<ChanPostImage>): List<ChanPostImage> {
    val threadDescriptors = parameter
      .map { chanPostImage -> chanPostImage.ownerPostDescriptor.threadDescriptor() }
      .toSet()

    val resultList = mutableListWithCap<ChanPostImage>(parameter.size / 2)

    threadDescriptors.forEach { threadDescriptor ->
      val chanPostHidesMap = postHideManager.getHiddenPostsForThread(threadDescriptor)
        .associateBy { chanPostHide -> chanPostHide.postDescriptor }

      parameter.forEach { chanPostImage ->
        if (chanPostHidesMap.containsKey(chanPostImage.ownerPostDescriptor)) {
          return@forEach
        }

        if (postFilterManager.getFilterStubOrRemove(chanPostImage.ownerPostDescriptor)) {
          return@forEach
        }

        resultList += chanPostImage
      }
    }

    return resultList
  }

}