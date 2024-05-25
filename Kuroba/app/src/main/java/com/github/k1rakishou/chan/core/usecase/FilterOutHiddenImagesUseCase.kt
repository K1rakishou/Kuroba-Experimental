package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostFilterManagerImpl
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.PostHideManagerImpl
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostHide

class FilterOutHiddenImagesUseCase(
  private val postHideManager: PostHideManager,
  private val postFilterManager: PostFilterManager
) {

  fun<T> filter(parameter: Input<T>): Output<T> {
    val images = parameter.images

    var prevSelectedElementIndex = parameter.index
    if (prevSelectedElementIndex != null && prevSelectedElementIndex >= images.size) {
      prevSelectedElementIndex = images.lastIndex
    }

    if (prevSelectedElementIndex != null && prevSelectedElementIndex < 0) {
      return Output<T>(parameter.images, parameter.index)
    }

    val isOpeningAlbum = parameter.isOpeningAlbum

    val groupedImages = images
      .groupBy { chanPostImage -> parameter.postDescriptorSelector(chanPostImage)?.threadDescriptor() }

    val resultList = mutableListWithCap<T>(images.size / 2)
    var groupsIndex = 0
    var hiddenElementsBeforeSelectedElementIndex = 0

    groupedImages.forEach { (threadDescriptor, chanPostImages) ->
      if (threadDescriptor == null) {
        resultList.addAll(chanPostImages)
        return@forEach
      }

      val chanPostHidesMap = postHideManager.getHiddenPostsForThread(threadDescriptor)
        .associateBy { chanPostHide -> chanPostHide.postDescriptor }

      chanPostImages.forEachIndexed { imageIndex, chanPostImage ->
        val globalImageIndex = groupsIndex + imageIndex

        val isHidden = isHidden(
          chanPostImage = chanPostImage,
          chanPostHidesMap = chanPostHidesMap,
          postDescriptorSelector = parameter.postDescriptorSelector
        )

        if (isHidden) {
          if (prevSelectedElementIndex != null && globalImageIndex <= prevSelectedElementIndex) {
            ++hiddenElementsBeforeSelectedElementIndex
          }

          return@forEachIndexed
        }

        resultList += chanPostImage
      }

      groupsIndex += 1
    }

    if (resultList.isEmpty()) {
      return Output(emptyList(), 0)
    }

    if (isOpeningAlbum) {
      val newIndex = if (prevSelectedElementIndex != null) {
        findNewIndex(
          prevSelectedElementIndex = prevSelectedElementIndex - hiddenElementsBeforeSelectedElementIndex,
          initialElements = images,
          resultElements = resultList,
          elementExistsInList = parameter.elementExistsInList
        )
      } else {
        null
      }

      return Output(resultList, newIndex)
    }

    val prevSelectedImage = if (prevSelectedElementIndex != null) {
      images.getOrNull(prevSelectedElementIndex) ?: 0
    } else {
      0
    }

    var newSelectedImageIndex = resultList.indexOfFirst { postImage -> postImage == prevSelectedImage }
    if (newSelectedImageIndex < 0) {
      // Just reset the scroll position to the beginning of the list
      newSelectedImageIndex = 0
    }

    return Output(resultList, newSelectedImageIndex)
  }

  private fun <T> isHidden(
    chanPostImage: T,
    chanPostHidesMap: Map<PostDescriptor, ChanPostHide>,
    postDescriptorSelector: (T) -> PostDescriptor?
  ): Boolean {
    val postDescriptor = postDescriptorSelector(chanPostImage)
    if (postDescriptor == null) {
      return true
    }

    val chanPostHide = chanPostHidesMap[postDescriptor]
    if (chanPostHide != null && !chanPostHide.manuallyRestored) {
      // Hidden or removed
      return true
    }

    if (postFilterManager.getFilterStubOrRemove(postDescriptor)) {
      return true
    }
    return false
  }

  private fun <T> findNewIndex(
    prevSelectedElementIndex: Int,
    initialElements: List<T>,
    resultElements: MutableList<T>,
    elementExistsInList: (T, List<T>) -> Boolean
  ): Int {
    // Since the image index we were about to scroll to may happen to be a hidden image, we need
    // to find the next image that exists in resultList (meaning it's not hidden).
    for (index in prevSelectedElementIndex until initialElements.size) {
      val image = resultElements.getOrNull(index)
        ?: break

      if (elementExistsInList(image, resultElements)) {
        return index
      }
    }

    return 0
  }

  data class Input<T>(
    val images: List<T>,
    val index: Int?,
    val isOpeningAlbum: Boolean,
    val postDescriptorSelector: (T) -> PostDescriptor?,
    val elementExistsInList: (T, List<T>) -> Boolean
  )

  data class Output<T>(
    val images: List<T>,
    val index: Int?
  )

}