package com.github.adamantcheese.database.source

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.dto.YoutubeLinkExtraContent
import com.github.adamantcheese.database.mapper.YoutubeLinkExtraContentMapper
import org.joda.time.DateTime

class YoutubeLinkExtraContentLocalSource(
        private val database: KurobaDatabase
) : AbstractLocalSource() {
    private val youtubeLinkExtraContentDao = database.youtubeLinkExtraContentDao()

    // TODO(ODL): add in-memory cache?

    suspend fun insert(youtubeLinkExtraContent: YoutubeLinkExtraContent): ModularResult<Unit> {
        return safeRun {
            return@safeRun youtubeLinkExtraContentDao.insert(
                    YoutubeLinkExtraContentMapper.toEntity(youtubeLinkExtraContent)
            )
        }
    }

    suspend fun getByPostUid(postUid: String, url: String): ModularResult<YoutubeLinkExtraContent?> {
        return safeRun {
            return@safeRun YoutubeLinkExtraContentMapper.fromEntity(
                    youtubeLinkExtraContentDao.getByPostUid(postUid, url)
            )
        }
    }

    suspend fun deleteOlderThanOneMonth(): ModularResult<Unit> {
        return safeRun {
            return@safeRun youtubeLinkExtraContentDao.deleteOlderThan(ONE_MONTH_AGO)
        }
    }

    companion object {
        private val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}