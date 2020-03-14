package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.dto.Loadable2
import com.github.adamantcheese.database.dto.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.source.Loadable2LocalSource
import com.github.adamantcheese.database.source.MediaServiceLinkExtraContentLocalSource
import java.util.concurrent.atomic.AtomicBoolean

class MediaServiceLinkExtraContentRepository(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger,
        private val loadable2LocalSource: Loadable2LocalSource,
        private val mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource
) : AbstractRepository(database) {
    private val TAG = "$loggerTag MSLECR"
    private val alreadyExecuted = AtomicBoolean(false)

    suspend fun insert(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent): ModularResult<Unit> {
        return insert(null, mediaServiceLinkExtraContent)
    }

    suspend fun insert(
            loadable2: Loadable2?,
            mediaServiceLinkExtraContent: MediaServiceLinkExtraContent
    ): ModularResult<Unit> {
        logger.log(TAG, "insert($loadable2, $mediaServiceLinkExtraContent)")

        return runInTransaction {
            mediaServiceLinkExtraContentRepositoryCleanup().unwrap()

            if (loadable2 != null) {
                loadable2LocalSource.insert(loadable2).unwrap()
            }

            return@runInTransaction mediaServiceLinkExtraContentLocalSource.insert(mediaServiceLinkExtraContent)
        }
    }

    suspend fun selectByPostUid(postUid: String, url: String): ModularResult<MediaServiceLinkExtraContent?> {
        return runInTransaction {
            mediaServiceLinkExtraContentRepositoryCleanup().unwrap()

            val result = mediaServiceLinkExtraContentLocalSource.selectByPostUid(postUid, url)
            logger.log(TAG, "selectByPostUid($postUid, $url) -> $result")

            return@runInTransaction result
        }
    }

    private suspend fun mediaServiceLinkExtraContentRepositoryCleanup(): ModularResult<Int> {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        check(isInTransaction()) { "Must be executed in a transaction!" }

        val result = mediaServiceLinkExtraContentLocalSource.deleteOlderThanOneMonth()
        if (result is ModularResult.Value) {
            logger.log(TAG, "cleanup() -> $result")
        } else {
            logger.logError(TAG, "cleanup() -> $result")
        }

        return result
    }
}