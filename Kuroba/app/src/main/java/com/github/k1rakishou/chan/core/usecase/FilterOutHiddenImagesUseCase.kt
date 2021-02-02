package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.post.ChanPostImage

class FilterOutHiddenImagesUseCase(
  private val postHideManager: PostHideManager,
  private val postFilterManager: PostFilterManager
) : IUseCase<FilterOutHiddenImagesUseCase.Input, FilterOutHiddenImagesUseCase.Output> {

  override fun execute(parameter: Input): Output {
    val chanPostImages = parameter.images
    val prevSelectedImage = chanPostImages[parameter.index]

    val groupedImages = chanPostImages
      .groupBy { chanPostImage -> chanPostImage.ownerPostDescriptor.threadDescriptor() }

    val resultList = mutableListWithCap<ChanPostImage>(chanPostImages.size / 2)

    groupedImages.forEach { (threadDescriptor, chanPostImages) ->
      val chanPostHidesMap = postHideManager.getHiddenPostsForThread(threadDescriptor)
        .associateBy { chanPostHide -> chanPostHide.postDescriptor }

      chanPostImages.forEach { chanPostImage ->
        if (chanPostHidesMap.containsKey(chanPostImage.ownerPostDescriptor)) {
          return@forEach
        }

        if (postFilterManager.getFilterStubOrRemove(chanPostImage.ownerPostDescriptor)) {
          return@forEach
        }

        resultList += chanPostImage
      }
    }

    val newSelectedImageIndex = resultList.indexOfFirst { postImage -> postImage == prevSelectedImage }
    if (newSelectedImageIndex < 0) {
      return Output(emptyList(), -1)
    }

    return Output(resultList, newSelectedImageIndex)
  }

  data class Input(
    val images: List<ChanPostImage>,
    val index: Int
  )

  data class Output(
    val images: List<ChanPostImage>,
    val index: Int
  )

}