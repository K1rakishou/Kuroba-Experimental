package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.data.video_service.MediaServiceType
import com.github.adamantcheese.database.source.local.Loadable2LocalSource
import com.github.adamantcheese.database.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.database.source.remote.MediaServiceLinkExtraContentRemoteSource
import java.util.concurrent.atomic.AtomicBoolean

class MediaServiceLinkExtraContentRepository(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger,
        private val loadable2LocalSource: Loadable2LocalSource,
        private val mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
        private val mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource
) : AbstractRepository(database) {
    private val TAG = "$loggerTag MSLECR"
    private val alreadyExecuted = AtomicBoolean(false)

    suspend fun getLinkExtraContent(
            loadableUid: String,
            postUid: String,
            mediaServiceType: MediaServiceType,
            requestUrl: String,
            originalUrl: String
    ): ModularResult<MediaServiceLinkExtraContent> {
        mediaServiceLinkExtraContentRepositoryCleanup().ignore()

        val localSourceResult = mediaServiceLinkExtraContentLocalSource.selectByPostUid(
                postUid,
                originalUrl
        )

        when (localSourceResult) {
            is ModularResult.Error -> return ModularResult.error(localSourceResult.error)
            is ModularResult.Value -> {
                if (localSourceResult.value != null) {
                    return ModularResult.value(localSourceResult.value!!)
                }

                // Fallthrough
            }
        }

        val fetchFromNetworkResult = mediaServiceLinkExtraContentRemoteSource.fetchFromNetwork(
                requestUrl,
                mediaServiceType
        )

        when (fetchFromNetworkResult) {
            is ModularResult.Error -> return ModularResult.error(fetchFromNetworkResult.error)
            is ModularResult.Value -> {
                val mediaServiceLinkExtraInfo = fetchFromNetworkResult.value

                val mediaServiceLinkExtraContent = MediaServiceLinkExtraContent(
                        postUid,
                        loadableUid,
                        mediaServiceType,
                        originalUrl,
                        mediaServiceLinkExtraInfo.videoTitle,
                        mediaServiceLinkExtraInfo.videoDuration
                )

                val storeResult = mediaServiceLinkExtraContentLocalSource.insert(
                        mediaServiceLinkExtraContent
                )

                when (storeResult) {
                    is ModularResult.Error -> return ModularResult.error(storeResult.error)
                    is ModularResult.Value -> {
                        return ModularResult.value(mediaServiceLinkExtraContent)
                    }
                }
            }
        }
    }

    private suspend fun mediaServiceLinkExtraContentRepositoryCleanup(): ModularResult<Int> {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        val result = mediaServiceLinkExtraContentLocalSource.deleteOlderThanOneMonth()
        if (result is ModularResult.Value) {
            logger.log(TAG, "cleanup() -> $result")
        } else {
            logger.logError(TAG, "cleanup() -> $result")
        }

        return result
    }
}