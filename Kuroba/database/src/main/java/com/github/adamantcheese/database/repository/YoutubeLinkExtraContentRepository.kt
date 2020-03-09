package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.dto.Loadable2
import com.github.adamantcheese.database.dto.YoutubeLinkExtraContent
import com.github.adamantcheese.database.source.Loadable2LocalSource
import com.github.adamantcheese.database.source.YoutubeLinkExtraContentLocalSource
import java.util.concurrent.atomic.AtomicBoolean

class YoutubeLinkExtraContentRepository(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger,
        private val loadable2LocalSource: Loadable2LocalSource,
        private val youtubeLinkExtraContentLocalSource: YoutubeLinkExtraContentLocalSource
) : AbstractRepository(database) {
    private val TAG = "$loggerTag YLECR"
    private val alreadyExecuted = AtomicBoolean(false)

    suspend fun insert(youtubeLinkExtraContent: YoutubeLinkExtraContent): ModularResult<Unit> {
        return insert(null, youtubeLinkExtraContent)
    }

    suspend fun insert(
            loadable2: Loadable2?,
            youtubeLinkExtraContent: YoutubeLinkExtraContent
    ): ModularResult<Unit> {
        logger.log(TAG, "insert($loadable2, $youtubeLinkExtraContent)")

        return runInTransaction {
            youtubeLinkExtraContentRepositoryCleanup().unwrap()

            if (loadable2 != null) {
                loadable2LocalSource.insert(loadable2).unwrap()
            }

            return@runInTransaction youtubeLinkExtraContentLocalSource.insert(youtubeLinkExtraContent)
        }
    }

    suspend fun selectByPostUid(postUid: String, url: String): ModularResult<YoutubeLinkExtraContent?> {
        return runInTransaction {
            youtubeLinkExtraContentRepositoryCleanup().unwrap()

            val result = youtubeLinkExtraContentLocalSource.selectByPostUid(postUid, url)
            logger.log(TAG, "selectByPostUid($postUid, $url) -> $result")

            return@runInTransaction result
        }
    }

    private suspend fun youtubeLinkExtraContentRepositoryCleanup(): ModularResult<Int> {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        check(isInTransaction()) { "Must be executed in a transaction!" }

        val result = youtubeLinkExtraContentLocalSource.deleteOlderThanOneMonth()
        if (result is ModularResult.Value) {
            logger.log(TAG, "youtubeLinkExtraContentRepositoryCleanup() -> $result")
        } else {
            logger.logError(TAG, "youtubeLinkExtraContentRepositoryCleanup() -> $result")
        }

        return result
    }
}