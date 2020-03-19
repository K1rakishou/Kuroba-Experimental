package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.mapper.MediaServiceLinkExtraContentMapper
import org.joda.time.DateTime

open class MediaServiceLinkExtraContentLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag MediaServiceLinkExtraContentLocalSource"
    private val mediaServiceLinkExtraContentDao = database.mediaServiceLinkExtraContentDao()

    open suspend fun insert(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent): ModularResult<Unit> {
        logger.log(TAG, "insert($mediaServiceLinkExtraContent)")

        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.insert(
                    MediaServiceLinkExtraContentMapper.toEntity(
                            mediaServiceLinkExtraContent,
                            DateTime.now()
                    )
            )
        }
    }

    open suspend fun selectByVideoId(videoId: String): ModularResult<MediaServiceLinkExtraContent?> {
        logger.log(TAG, "selectByVideoId($videoId)")

        return safeRun {
            return@safeRun MediaServiceLinkExtraContentMapper.fromEntity(
                    mediaServiceLinkExtraContentDao.selectByVideoId(videoId)
            )
        }
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_WEEK_AGO): ModularResult<Int> {
        logger.log(TAG, "deleteOlderThan($dateTime)")

        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.deleteOlderThan(dateTime)
        }
    }

    companion object {
        val ONE_WEEK_AGO = DateTime.now().minusWeeks(1)
    }
}