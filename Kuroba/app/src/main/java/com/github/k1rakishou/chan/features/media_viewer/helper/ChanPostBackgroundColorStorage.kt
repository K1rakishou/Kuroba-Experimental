package com.github.k1rakishou.chan.features.media_viewer.helper

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

/**
 * A storage for the default post background color per site.
 * */
class ChanPostBackgroundColorStorage(
  private val boardManager: BoardManager
) {
  private val colors by lazy {
    val colorMap = mutableMapOf<SiteDescriptor, ChanBackgroundColors>()

    colorMap[Chan4.SITE_DESCRIPTOR] = ChanBackgroundColors(
      sfwColor = DEFAULT_COLOR,
      nsfwColor = 0xF0E0D6L.toInt()
    )

    colorMap[Dvach.SITE_DESCRIPTOR] = ChanBackgroundColors(
      sfwColor = 0xDDDDDDL.toInt(),
      nsfwColor = 0xDDDDDDL.toInt()
    )

    // TODO: 6/5/2021 add colors for the rest of the sites

    return@lazy colorMap
  }

  fun getBackgroundColor(postDescriptor: PostDescriptor?): Int {
    if (postDescriptor == null) {
      return DEFAULT_COLOR
    }

    val chanBackgroundColors = colors[postDescriptor.siteDescriptor()]
    if (chanBackgroundColors == null) {
      return DEFAULT_COLOR
    }

    val workSafeBoard = boardManager.byBoardDescriptor(postDescriptor.boardDescriptor())?.workSafe
    if (workSafeBoard == null) {
      return chanBackgroundColors.sfwColor
    }

    if (workSafeBoard) {
      return chanBackgroundColors.sfwColor
    }

    return chanBackgroundColors.nsfwColor
  }

  data class ChanBackgroundColors(val sfwColor: Int, val nsfwColor: Int)

  companion object {
    private const val DEFAULT_COLOR = 0xD6DAF0L.toInt()
  }
}