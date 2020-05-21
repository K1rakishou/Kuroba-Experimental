package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.model.PostImage
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor

class PrefetchImageDownloadIndicatorManager {
  private val prefetchStateProcessor = PublishProcessor.create<PrefetchState>().toSerialized()

  fun onPrefetchStarted(postImage: PostImage) {
    if (postImage.isPrefetched) {
      return
    }

    prefetchStateProcessor.onNext(PrefetchState.PrefetchStarted(postImage))
  }

  fun onPrefetchProgress(postImage: PostImage, progress: Float) {
    if (postImage.isPrefetched) {
      return
    }

    prefetchStateProcessor.onNext(PrefetchState.PrefetchProgress(postImage, progress))
  }

  fun onPrefetchCompleted(postImage: PostImage) {
    if (!postImage.isPrefetched) {
      return
    }

    prefetchStateProcessor.onNext(PrefetchState.PrefetchCompleted(postImage))
  }

  fun listenForPrefetchStateUpdates(): Flowable<PrefetchState> {
    return prefetchStateProcessor
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

}

sealed class PrefetchState(val postImage: PostImage) {
  class PrefetchStarted(postImage: PostImage) : PrefetchState(postImage)
  class PrefetchProgress(postImage: PostImage, val progress: Float) : PrefetchState(postImage)
  class PrefetchCompleted(postImage: PostImage) : PrefetchState(postImage)
}