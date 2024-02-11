package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheFileType
import okhttp3.HttpUrl

interface ChunkedMediaDownloader {
    fun clearCache(cacheFileType: CacheFileType)
    fun isRunning(url: String): Boolean
    fun isRunning(url: HttpUrl): Boolean

    fun enqueueDownloadFileRequest(
        mediaUrl: HttpUrl,
        cacheFileType: CacheFileType,
        extraInfo: DownloadRequestExtraInfo = DownloadRequestExtraInfo(),
        callback: FileCacheListener? = null,
    ): CancelableDownload?

}