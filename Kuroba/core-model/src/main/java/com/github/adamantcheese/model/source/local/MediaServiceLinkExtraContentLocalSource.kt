package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.model.mapper.MediaServiceLinkExtraContentMapper
import com.github.adamantcheese.model.util.ensureBackgroundThread
import org.joda.time.DateTime

open class MediaServiceLinkExtraContentLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag MediaServiceLinkExtraContentLocalSource"
    private val mediaServiceLinkExtraContentDao = database.mediaServiceLinkExtraContentDao()

    open suspend fun insert(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent): ModularResult<Unit> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.insert(
                    MediaServiceLinkExtraContentMapper.toEntity(
                            mediaServiceLinkExtraContent,
                            DateTime.now()
                    )
            ).also { result -> logger.log(TAG, "insert($mediaServiceLinkExtraContent) -> $result") }
        }
    }

    open suspend fun selectByVideoId(videoId: String): ModularResult<MediaServiceLinkExtraContent?> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun MediaServiceLinkExtraContentMapper.fromEntity(
                    mediaServiceLinkExtraContentDao.selectByVideoId(videoId)
            ).also { result -> logger.log(TAG, "selectByVideoId($videoId) -> $result") }
        }
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_WEEK_AGO): ModularResult<Int> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.deleteOlderThan(dateTime)
                    .also { result -> logger.log(TAG, "deleteOlderThan($dateTime) -> $result") }
        }
    }

    open suspend fun deleteAll(): ModularResult<Int> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.deleteAll()
                    .also { result -> logger.log(TAG, "deleteAll() -> $result") }
        }
    }

    companion object {
        val ONE_WEEK_AGO = DateTime.now().minusWeeks(1)
    }
}