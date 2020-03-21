package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.model.data.video_service.MediaServiceType
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.github.adamantcheese.model.util.ensureBackgroundThread
import java.util.concurrent.atomic.AtomicBoolean

class MediaServiceLinkExtraContentRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val cache: GenericCacheSource<String, MediaServiceLinkExtraContent>,
        private val mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
        private val mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag MediaServiceLinkExtraContentRepository"
    private val alreadyExecuted = AtomicBoolean(false)

    suspend fun getLinkExtraContent(
            mediaServiceType: MediaServiceType,
            requestUrl: String,
            videoId: String
    ): ModularResult<MediaServiceLinkExtraContent> {
        ensureBackgroundThread()

        return repoGenericGetAction(
                cleanupFunc = { mediaServiceLinkExtraContentRepositoryCleanup().ignore() },
                getFromCacheFunc = { cache.get(videoId) },
                getFromLocalSourceFunc = {
                    mediaServiceLinkExtraContentLocalSource.selectByVideoId(videoId)
                },
                getFromRemoteSourceFunc = {
                    mediaServiceLinkExtraContentRemoteSource.fetchFromNetwork(
                            requestUrl,
                            mediaServiceType
                    ).map {
                        MediaServiceLinkExtraContent(
                                videoId,
                                mediaServiceType,
                                it.videoTitle,
                                it.videoDuration
                        )
                    }
                },
                storeIntoCacheFunc = { mediaServiceLinkExtraContent ->
                        cache.store(requestUrl, mediaServiceLinkExtraContent)
                },
                storeIntoLocalSourceFunc = { mediaServiceLinkExtraContent ->
                    mediaServiceLinkExtraContentLocalSource.insert(mediaServiceLinkExtraContent)
                },
                tag = TAG
        )
    }

    suspend fun isCached(videoId: String): Boolean {
        ensureBackgroundThread()

        val hasInCache = cache.contains(videoId)
        if (hasInCache) {
            return true
        }

        return when (val result = mediaServiceLinkExtraContentLocalSource.selectByVideoId(videoId)) {
            is ModularResult.Value -> result.value != null
            is ModularResult.Error -> {
                logger.logError(TAG, "Error while trying to selectByVideoId($videoId)", result.error)
                false
            }
        }
    }

    private suspend fun mediaServiceLinkExtraContentRepositoryCleanup(): ModularResult<Int> {
        ensureBackgroundThread()

        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        val result = mediaServiceLinkExtraContentLocalSource.deleteOlderThan(
                MediaServiceLinkExtraContentLocalSource.ONE_WEEK_AGO
        )

        if (result is ModularResult.Value) {
            logger.log(TAG, "cleanup() -> $result")
        } else {
            logger.logError(TAG, "cleanup() -> $result")
        }

        return result
    }

}