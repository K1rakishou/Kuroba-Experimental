package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.SeenPost
import com.github.adamantcheese.database.mapper.SeenPostMapper
import org.joda.time.DateTime

class SeenPostLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag MediaServiceLinkExtraContentLocalSource"
    private val seenPostDao = database.seenPostDao()

    suspend fun insert(seenPost: SeenPost): ModularResult<Unit> {
        logger.log(TAG, "insert($seenPost)")

        return safeRun {
            return@safeRun seenPostDao.insert(
                    SeenPostMapper.toEntity(seenPost)
            )
        }
    }

    suspend fun selectAllByLoadableUid(loadableUid: String): ModularResult<List<SeenPost>> {
        logger.log(TAG, "selectAllByLoadableUid($loadableUid)")

        return safeRun {
            return@safeRun seenPostDao.selectAllByLoadableUid(loadableUid)
                    .mapNotNull { seenPostEntity -> SeenPostMapper.fromEntity(seenPostEntity) }
        }
    }

    suspend fun deleteOlderThanOneMonth(): ModularResult<Int> {
        logger.log(TAG, "deleteOlderThanOneMonth()")

        return safeRun {
            return@safeRun seenPostDao.deleteOlderThan(ONE_MONTH_AGO)
        }
    }

    companion object {
        private val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}