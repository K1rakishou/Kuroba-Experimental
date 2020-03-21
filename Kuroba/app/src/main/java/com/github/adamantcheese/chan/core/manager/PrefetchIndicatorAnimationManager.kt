package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.model.PostImage
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor

class PrefetchIndicatorAnimationManager {
    private val prefetchStateProcessor = PublishProcessor.create<PrefetchStateData>()

    fun onPrefetchDone(postImage: PostImage) {
        if (!postImage.isPrefetched) {
            return
        }

        prefetchStateProcessor.onNext(PrefetchStateData(postImage))
    }

    fun listenForPrefetchStateUpdates(): Flowable<PrefetchStateData> {
        return prefetchStateProcessor
                .observeOn(AndroidSchedulers.mainThread())
                .hide()
    }

    class PrefetchStateData(val postImage: PostImage) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PrefetchStateData) return false

            if (postImage != other.postImage) return false

            return true
        }

        override fun hashCode(): Int {
            return postImage.hashCode()
        }

        override fun toString(): String {
            return "PrefetchStateData(postImage=$postImage"
        }

    }
}