package com.github.adamantcheese.chan.core.loader

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
     * Loader rejected to load new content for current post (feature is turned off in settings
     * by the user or some other condition is not satisfied (like we are currently not on Wi-Fi
     * network and the loader requires Wi-Fi connection to load huge content, like prefetcher)
     * */
    class Rejected(loaderType: LoaderType) : LoaderResult(loaderType)
}