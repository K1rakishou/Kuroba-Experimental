package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.model.data.post.LoaderType

abstract class OnDemandContentLoader(
  val loaderType: LoaderType
) {
  abstract suspend fun isCached(postLoaderData: PostLoaderData): Boolean
  abstract suspend fun startLoading(postLoaderData: PostLoaderData): LoaderResult
  abstract fun cancelLoading(postLoaderData: PostLoaderData)

  protected fun succeeded(needUpdateView: Boolean): LoaderResult {
    return LoaderResult.Succeeded(loaderType, needUpdateView)
  }

  protected fun failed(): LoaderResult = LoaderResult.Failed(loaderType)
  protected fun rejected(): LoaderResult = LoaderResult.Rejected(loaderType)

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (other !is OnDemandContentLoader) {
      return false
    }

    if (loaderType != other.loaderType) {
      return false
    }

    return true
  }

  override fun hashCode(): Int {
    return loaderType.hashCode()
  }

}