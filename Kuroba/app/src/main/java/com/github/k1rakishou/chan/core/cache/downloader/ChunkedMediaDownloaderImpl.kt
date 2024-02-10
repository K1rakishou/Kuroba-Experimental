package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.fsaf.FileManager
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl

class ChunkedMediaDownloaderImpl(
    private val appScope: CoroutineScope,
    private val fileManager: FileManager,
    private val siteResolver: SiteResolver,
    private val cacheHandlerLazy: Lazy<CacheHandler>,
    private val downloaderOkHttpClientLazy: Lazy<RealDownloaderOkHttpClient>
) : ChunkedMediaDownloader {
    private val cacheHandler: CacheHandler
        get() = cacheHandlerLazy.get()
    private val downloaderOkHttpClient: RealDownloaderOkHttpClient
        get() = downloaderOkHttpClientLazy.get()

    private val activeDownloads = ActiveDownloads()

    override fun clearCache(cacheFileType: CacheFileType) {
        activeDownloads.clear()
        cacheHandler.clearCache(cacheFileType)
    }

    override fun isRunning(url: String): Boolean {
        return synchronized(activeDownloads) {
            activeDownloads.getState(url) == DownloadState.Running
        }
    }

    override fun enqueueDownloadFileRequest(
        url: HttpUrl,
        cacheFileType: CacheFileType,
        extraInfo: DownloadRequestExtraInfo,
        callback: FileCacheListener?,
    ): CancelableDownload? {
        TODO()
    }

}