package com.github.k1rakishou.chan.ui.cell

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

/**
 * The point of this class is to cache post cell's total width which is then used to calculate the
 * amount of spans that can be used for post with multiple images. We need to have these values
 * (post width) separated for 1) Catalog post views, 2) Thread post views, 3) Post popups. That's
 * because they all may have different parents width. Once more states are added (similar to those 3)
 * this class needs to be updated. This is only needed for posts with multiple images. CardPostCells
 * are not needed in this class since they can only have one thumbnail.
 * */
object PostCellWidthStorage {
  private const val INDEX_CATALOG = 0
  private const val INDEX_THREAD = 1
  private const val INDEX_POPUP = 2

  private var CACHED_POST_CELL_WIDTH = IntArray(3)

  fun update(postCellData: PostCellData, postCellWidth: Int) {
    val index = postCellDataToArrayIndex(postCellData)
    CACHED_POST_CELL_WIDTH[index] = postCellWidth
  }

  fun get(postCellData: PostCellData): Int {
    val index = postCellDataToArrayIndex(postCellData)
    return CACHED_POST_CELL_WIDTH[index]
  }

  fun reset() {
    CACHED_POST_CELL_WIDTH.fill(0)
  }

  private fun postCellDataToArrayIndex(postCellData: PostCellData): Int {
    if (postCellData.isInPopup) {
      return INDEX_POPUP
    }

    return when (postCellData.chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> INDEX_CATALOG
      is ChanDescriptor.ThreadDescriptor -> INDEX_THREAD
    }
  }
}