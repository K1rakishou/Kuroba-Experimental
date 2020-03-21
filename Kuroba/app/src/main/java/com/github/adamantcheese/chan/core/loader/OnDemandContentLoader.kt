package com.github.adamantcheese.chan.core.loader

import io.reactivex.Single

abstract class OnDemandContentLoader(
        val loaderType: LoaderType
) {
    abstract fun isCached(postLoaderData: PostLoaderData): Single<Boolean>
    abstract fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult>
    abstract fun cancelLoading(postLoaderData: PostLoaderData)

    protected fun succeeded(needUpdateView: Boolean): Single<LoaderResult> {
        return Single.just(LoaderResult.Succeeded(loaderType, needUpdateView))
    }
    protected fun failed(): Single<LoaderResult> = Single.just(LoaderResult.Failed(loaderType))
    protected fun rejected(): Single<LoaderResult> = Single.just(LoaderResult.Rejected(loaderType))

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