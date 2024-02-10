package com.github.k1rakishou.chan.core.cache

import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import okhttp3.HttpUrl

interface ChunkedMediaDownloader {
    fun clearCache(cacheFileType: CacheFileType)
    fun isRunning(url: String): Boolean

    fun enqueueDownloadFileRequest(
        url: HttpUrl,
        cacheFileType: CacheFileType,
        extraInfo: DownloadRequestExtraInfo = DownloadRequestExtraInfo(),
        callback: FileCacheListener? = null,
    ): CancelableDownload?

}