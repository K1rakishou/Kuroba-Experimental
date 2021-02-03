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
    val prevSelectedImageIndex = parameter.index
    val prevSelectedImage = chanPostImages[prevSelectedImageIndex]
    val isOpeningAlbum = parameter.isOpeningAlbum

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

    if (isOpeningAlbum) {
      var newIndex = 0

      // Since the image index we were about to scroll to may happen to be of a hidden image, we need
      // to find the next image that exists in resultList (meaning it's not hidden).
      for (index in prevSelectedImageIndex until chanPostImages.size) {
        val image = resultList.getOrNull(index)
          ?: break

        if (image in resultList) {
          newIndex = index
          break
        }
      }

      return Output(resultList, newIndex)
    }

    var newSelectedImageIndex = resultList.indexOfFirst { postImage -> postImage == prevSelectedImage }
    if (newSelectedImageIndex < 0) {
      // Just reset the scroll position to the beginning of the list
      newSelectedImageIndex = 0
    }

    return Output(resultList, newSelectedImageIndex)
  }

  data class Input(
    val images: List<ChanPostImage>,
    val index: Int,
    val isOpeningAlbum: Boolean
  )

  data class Output(
    val images: List<ChanPostImage>,
    val index: Int
  )

}