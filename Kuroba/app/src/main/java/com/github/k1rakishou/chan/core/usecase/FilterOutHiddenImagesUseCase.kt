package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class FilterOutHiddenImagesUseCase(
  private val postHideManager: PostHideManager,
  private val postFilterManager: PostFilterManager
) {

  fun<T> filter(parameter: Input<T>): Output<T> {
    val images = parameter.images

    var prevSelectedImageIndex = parameter.index
    if (prevSelectedImageIndex >= images.size) {
      prevSelectedImageIndex = images.lastIndex
    }

    if (prevSelectedImageIndex < 0) {
      return Output<T>(parameter.images, parameter.index)
    }

    val prevSelectedImage = images[prevSelectedImageIndex]
    val isOpeningAlbum = parameter.isOpeningAlbum

    val groupedImages = images
      .groupBy { chanPostImage -> parameter.postDescriptorSelector(chanPostImage)?.threadDescriptor() }

    val resultList = mutableListWithCap<T>(images.size / 2)

    groupedImages.forEach { (threadDescriptor, chanPostImages) ->
      if (threadDescriptor == null) {
        resultList.addAll(chanPostImages)
        return@forEach
      }

      val chanPostHidesMap = postHideManager.getHiddenPostsForThread(threadDescriptor)
        .associateBy { chanPostHide -> chanPostHide.postDescriptor }

      chanPostImages.forEach { chanPostImage ->
        val postDescriptor = parameter.postDescriptorSelector(chanPostImage)
          ?: return@forEach

        val chanPostHide = chanPostHidesMap[postDescriptor]

        if (chanPostHide != null && !chanPostHide.manuallyRestored) {
          // Hidden or removed
          return@forEach
        }

        if (postFilterManager.getFilterStubOrRemove(postDescriptor)) {
          return@forEach
        }

        resultList += chanPostImage
      }
    }

    if (resultList.isEmpty()) {
      return Output(emptyList(), 0)
    }

    if (isOpeningAlbum) {
      var newIndex = 0

      // Since the image index we were about to scroll to may happen to be a hidden image, we need
      // to find the next image that exists in resultList (meaning it's not hidden).
      for (index in prevSelectedImageIndex until images.size) {
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

  data class Input<T>(
    val images: List<T>,
    val index: Int,
    val isOpeningAlbum: Boolean,
    val postDescriptorSelector: (T) -> PostDescriptor?
  )

  data class Output<T>(
    val images: List<T>,
    val index: Int
  )

}