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

}