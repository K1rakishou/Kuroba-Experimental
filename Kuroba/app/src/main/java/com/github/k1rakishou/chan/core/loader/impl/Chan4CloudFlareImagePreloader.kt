package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.LoaderType
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import io.reactivex.Single

class Chan4CloudFlareImagePreloader(
  private val chan4CloudFlareImagePreloaderManager: Chan4CloudFlareImagePreloaderManager
) : OnDemandContentLoader(LoaderType.Chan4CloudFlareImagePreLoader) {

  override fun isCached(postLoaderData: PostLoaderData): Single<Boolean> {
    val isCached = chan4CloudFlareImagePreloaderManager.isCached(
      postLoaderData.post.postDescriptor
    )

    return Single.just(isCached)
  }

  override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
    BackgroundUtils.ensureBackgroundThread()

    if (!chan4CloudFlareImagePreloaderManager.startLoading(postLoaderData.post.postDescriptor)) {
      return rejected()
    }

    return succeeded(needUpdateView = false)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    chan4CloudFlareImagePreloaderManager.cancelLoading(postLoaderData.post.postDescriptor)
  }

  companion object {
    private const val TAG = "Chan4CloudFlareImagePreloader"
  }
}