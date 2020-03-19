package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.SeenPost
import com.github.adamantcheese.database.mapper.SeenPostMapper
import org.joda.time.DateTime

open class SeenPostLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag MediaServiceLinkExtraContentLocalSource"
    private val seenPostDao = database.seenPostDao()

    open suspend fun insert(seenPost: SeenPost): ModularResult<Unit> {
        logger.log(TAG, "insert($seenPost)")

        return safeRun {
            return@safeRun seenPostDao.insert(
                    SeenPostMapper.toEntity(seenPost)
            )
        }
    }

    open suspend fun selectAllByLoadableUid(loadableUid: String): ModularResult<List<SeenPost>> {
        logger.log(TAG, "selectAllByLoadableUid($loadableUid)")

        return safeRun {
            return@safeRun seenPostDao.selectAllByLoadableUid(loadableUid)
                    .mapNotNull { seenPostEntity -> SeenPostMapper.fromEntity(seenPostEntity) }
        }
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_MONTH_AGO): ModularResult<Int> {
        logger.log(TAG, "deleteOlderThan($dateTime)")

        return safeRun {
            return@safeRun seenPostDao.deleteOlderThan(dateTime)
        }
    }

    companion object {
        val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}