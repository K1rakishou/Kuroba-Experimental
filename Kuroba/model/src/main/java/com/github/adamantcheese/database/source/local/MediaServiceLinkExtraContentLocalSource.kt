package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.mapper.MediaServiceLinkExtraContentMapper
import org.joda.time.DateTime

class MediaServiceLinkExtraContentLocalSource(
        private val database: KurobaDatabase
) : AbstractLocalSource() {
    private val mediaServiceLinkExtraContentDao = database.mediaServiceLinkExtraContentDao()

    // TODO(ODL): add in-memory cache?

    suspend fun insert(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent): ModularResult<Unit> {
        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.insert(
                    MediaServiceLinkExtraContentMapper.toEntity(
                            mediaServiceLinkExtraContent,
                            DateTime.now()
                    )
            )
        }
    }

    suspend fun selectByPostUid(postUid: String, originalUrl: String): ModularResult<MediaServiceLinkExtraContent?> {
        return safeRun {
            return@safeRun MediaServiceLinkExtraContentMapper.fromEntity(
                    mediaServiceLinkExtraContentDao.selectByPostUidAndVideoUrl(postUid, originalUrl)
            )
        }
    }

    suspend fun deleteOlderThanOneMonth(): ModularResult<Int> {
        return safeRun {
            return@safeRun mediaServiceLinkExtraContentDao.deleteOlderThan(ONE_MONTH_AGO)
        }
    }

    companion object {
        private val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}