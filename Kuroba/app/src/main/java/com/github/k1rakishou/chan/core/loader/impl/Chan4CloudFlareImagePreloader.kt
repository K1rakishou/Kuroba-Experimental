package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.model.data.post.LoaderType

class Chan4CloudFlareImagePreloader(
  private val chan4CloudFlareImagePreloaderManager: Chan4CloudFlareImagePreloaderManager
) : OnDemandContentLoader(LoaderType.Chan4CloudFlareImagePreLoader) {

  override suspend fun isCached(postLoaderData: PostLoaderData): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    return chan4CloudFlareImagePreloaderManager.isCached(postLoaderData.postDescriptor)
  }

  override suspend fun startLoading(postLoaderData: PostLoaderData): LoaderResult {
    BackgroundUtils.ensureBackgroundThread()

    if (!chan4CloudFlareImagePreloaderManager.startLoading(postLoaderData.postDescriptor)) {
      return rejected()
    }

    return succeeded(needUpdateView = false, loaderResultData = null)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    chan4CloudFlareImagePreloaderManager.cancelLoading(postLoaderData.postDescriptor)
  }

  companion object {
    private const val TAG = "Chan4CloudFlareImagePreloader"
  }
}