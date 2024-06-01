package com.github.k1rakishou.chan.ui.config

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.helper.AppResources

class ThumbnailsConfiguration(
  private val appResources: AppResources
) {
  private val cellPostThumbnailMaxSize = appResources.dimension(R.dimen.cell_post_thumbnail_size_max)

  private val density: Density
    get() = appResources.composeDensity
  private val isTablet: Boolean
    get() = appResources.isTablet

  fun postThumbnailSizeDp(): Dp {
    return with(density) { postThumbnailSizePx().toDp() }
  }

  fun postThumbnailSizePx(): Int {
    val postCellThumbnailSizePercent = cellPostThumbnailMaxSize / 100f
    val newSize = ChanSettings.postCellThumbnailSizePercents.get() * postCellThumbnailSizePercent

    return (newSize).toInt()
  }

}