package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.mapper.MediaServiceLinkExtraContentMapper
import org.joda.time.DateTime

class MediaServiceLinkExtraContentLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag MediaServiceLinkExtraContentLocalSource"
    private val mediaServiceLinkExtraContentDao = database.mediaServiceLinkExtraContentDao()

    suspend fun insert(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent): ModularResult<Unit> {
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

    suspend fun selectByVideoId(videoId: String): ModularResult<MediaServiceLinkExtraContent?> {
        logger.log(TAG, "selectByVideoId($videoId)")

        return safeRun {
            return@safeRun MediaServiceLinkExtraContentMapper.fromEntity(
                    mediaServiceLinkExtraContentDao.selectByVideoId(videoId)
            )
        }
    }

    suspend fun deleteOlderThanOneWeek(): ModularResult<Int> {
        logger.log(TAG, "deleteOlderThanOneWeek()")

        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.deleteOlderThan(ONE_WEEK_AGO)
        }
    }

    companion object {
        private val ONE_WEEK_AGO = DateTime.now().minusWeeks(1)
    }
}