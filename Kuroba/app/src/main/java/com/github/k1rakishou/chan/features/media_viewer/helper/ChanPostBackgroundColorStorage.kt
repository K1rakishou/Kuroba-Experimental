package com.github.k1rakishou.chan.features.media_viewer.helper

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

/**
 * A storage for the default post background color per site. Used as a background for transparent
 * images/gifs if "transparencyOn" setting is disabled.
 * */
class ChanPostBackgroundColorStorage(
  private val boardManager: BoardManager,
  private val siteResolver: SiteResolver
) {
  private val colors by lazy {
    val colorMap = mutableMapOf<SiteDescriptor, ChanBackgroundColors>()

    colorMap[Chan4.SITE_DESCRIPTOR] = ChanBackgroundColors(
      sfwColor = DEFAULT_COLOR,
      nsfwColor = 0xFFF0E0D6L.toInt()
    )

    colorMap[Dvach.SITE_DESCRIPTOR] = ChanBackgroundColors(
      sfwColor = 0xFFDDDDDDL.toInt(),
      nsfwColor = 0xFFDDDDDDL.toInt()
    )

    // TODO: 6/5/2021 add colors for the rest of the sites

    return@lazy colorMap
  }

  fun getBackgroundColor(mediaLocation: MediaLocation, postDescriptor: PostDescriptor?): Int {
    if (mediaLocation is MediaLocation.Local) {
      return DEFAULT_COLOR
    }

    val url = (mediaLocation as MediaLocation.Remote).url.toString()

    val siteDescriptor = postDescriptor?.siteDescriptor()
      ?: siteResolver.findSiteForUrl(url)

    if (siteDescriptor == null) {
      return DEFAULT_COLOR
    }

    val chanBackgroundColors = colors[siteDescriptor]
    if (chanBackgroundColors == null) {
      return DEFAULT_COLOR
    }

    val workSafeBoard = postDescriptor?.boardDescriptor()?.let { boardDescriptor ->
      boardManager.byBoardDescriptor(boardDescriptor)?.workSafe
    } ?: true

    if (workSafeBoard) {
      return chanBackgroundColors.sfwColor
    }

    return chanBackgroundColors.nsfwColor
  }

  data class ChanBackgroundColors(val sfwColor: Int, val nsfwColor: Int)

  companion object {
    private const val DEFAULT_COLOR = 0xFFD6DAF0L.toInt()
  }
}