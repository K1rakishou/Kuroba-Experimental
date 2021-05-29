package com.github.k1rakishou.chan.features.media_viewer.helper

import android.content.Context
import com.github.k1rakishou.common.AppConstants
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache

class ExoPlayerCache(
  context: Context,
  appConstants: AppConstants
) {
  val actualCache by lazy {
    SimpleCache(
      appConstants.exoPlayerCacheDir,
      LeastRecentlyUsedCacheEvictor(appConstants.exoPlayerDiskCacheMaxSize),
      ExoDatabaseProvider(context)
    )
  }
}