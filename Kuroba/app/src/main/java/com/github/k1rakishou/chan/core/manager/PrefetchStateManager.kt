package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPostImage
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor

class PrefetchStateManager {
  private val prefetchStateProcessor = PublishProcessor.create<PrefetchState>().toSerialized()

  fun onPrefetchStarted(postImage: ChanPostImage) {
    if (postImage.isPrefetched) {
      return
    }

    prefetchStateProcessor.onNext(PrefetchState.PrefetchStarted(postImage))
  }

  fun onPrefetchProgress(postImage: ChanPostImage, progress: Float) {
    if (postImage.isPrefetched) {
      return
    }

    prefetchStateProcessor.onNext(PrefetchState.PrefetchProgress(postImage, progress))
  }

  fun onPrefetchCompleted(postImage: ChanPostImage) {
    if (!postImage.isPrefetched) {
      return
    }

    prefetchStateProcessor.onNext(PrefetchState.PrefetchCompleted(postImage))
  }

  fun listenForPrefetchStateUpdates(): Flowable<PrefetchState> {
    return prefetchStateProcessor
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for prefetch state updates", error) }
      .hide()
  }

  companion object {
    private const val TAG = "PrefetchImageDownloadIndicatorManager"
  }
}

sealed class PrefetchState(val postImage: ChanPostImage) {
  class PrefetchStarted(postImage: ChanPostImage) : PrefetchState(postImage)
  class PrefetchProgress(postImage: ChanPostImage, val progress: Float) : PrefetchState(postImage)
  class PrefetchCompleted(postImage: ChanPostImage) : PrefetchState(postImage)
}