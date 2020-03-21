package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.SeenPost
import com.github.adamantcheese.model.mapper.SeenPostMapper
import com.github.adamantcheese.model.util.ensureBackgroundThread
import org.joda.time.DateTime

open class SeenPostLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag SeenPostLocalSource"
    private val seenPostDao = database.seenPostDao()

    open suspend fun insert(seenPost: SeenPost): ModularResult<Unit> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun seenPostDao.insert(SeenPostMapper.toEntity(seenPost))
                    .also { result -> logger.log(TAG, "insert($seenPost) -> $result") }
        }
    }

    open suspend fun selectAllByLoadableUid(loadableUid: String): ModularResult<List<SeenPost>> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun seenPostDao.selectAllByLoadableUid(loadableUid)
                    .mapNotNull { seenPostEntity -> SeenPostMapper.fromEntity(seenPostEntity) }
                    .also { result -> logger.log(TAG, "selectAllByLoadableUid($loadableUid) -> $result") }
        }
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_MONTH_AGO): ModularResult<Int> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun seenPostDao.deleteOlderThan(dateTime)
                    .also { result -> logger.log(TAG, "deleteOlderThan($dateTime) -> $result") }
        }
    }

    open suspend fun deleteAll(): ModularResult<Int> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun seenPostDao.deleteAll()
                    .also { result -> logger.log(TAG, "deleteAll() -> $result") }
        }
    }

    companion object {
        val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}