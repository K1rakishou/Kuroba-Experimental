package com.github.adamantcheese.chan.core.manager.loader

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import io.reactivex.Completable
import io.reactivex.Single

abstract class OnDemandContentLoader(
        val loaderType: LoaderType
) {
    abstract fun isAlreadyCached(loadable: Loadable, post: Post): Boolean
    abstract fun startLoading(loadable: Loadable, post: Post): Single<LoaderResult>
    abstract fun cancelLoading(loadable: Loadable, post: Post): Completable

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

    enum class LoaderType {
        PrefetchLoader,
        YoutubeLinkTitlesLoader,
        YoutubeLinkDurationsLoader,
        InlinedFileSizeLoader
    }

    data class LoaderBatchResult(
            val loadable: Loadable,
            val post: Post,
            val results: List<LoaderResult>
    )

    sealed class LoaderResult(val loaderType: LoaderType) {
        /**
         * Loader has successfully loaded new content for current post
         * */
        class Success(loaderType: LoaderType) : LoaderResult(loaderType)
        /**
         * Loader failed to load new content for current post (no internet connection or something
         * similar)
         * */
        class Error(loaderType: LoaderType) : LoaderResult(loaderType)
        /**
         * Loader rejected to load new content for current post (feature is turned off in setting
         * by the user or some other condition is not satisfied, like we are currently not on Wi-Fi
         * network and the loader type is PrefetchLoader.
         * */
        class Rejected(loaderType: LoaderType) : LoaderResult(loaderType)
    }


}