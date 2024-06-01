package com.github.k1rakishou.chan.ui.config

import com.github.k1rakishou.chan.ui.helper.AppResources

interface UiConfiguration {
  val thumbnails: ThumbnailsConfiguration
}

class UiConfigurationImpl(
  private val appResources: AppResources
) : UiConfiguration {
  override val thumbnails by lazy { ThumbnailsConfiguration(appResources) }
}