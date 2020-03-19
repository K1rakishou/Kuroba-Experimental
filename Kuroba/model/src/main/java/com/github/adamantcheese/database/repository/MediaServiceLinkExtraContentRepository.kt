package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import com.github.adamantcheese.database.source.cache.GenericCacheSource
import com.github.adamantcheese.database.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.database.source.remote.MediaServiceLinkExtraContentRemoteSource
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

    private suspend fun mediaServiceLinkExtraContentRepositoryCleanup(): ModularResult<Int> {
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