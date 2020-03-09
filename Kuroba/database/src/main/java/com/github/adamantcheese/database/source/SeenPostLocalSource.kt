package com.github.adamantcheese.database.source

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.dto.SeenPost
import com.github.adamantcheese.database.mapper.SeenPostMapper
import org.joda.time.DateTime

class SeenPostLocalSource(
        private val database: KurobaDatabase
) : AbstractLocalSource() {
    private val seenPostDao = database.seenPostDao()

    suspend fun insert(seenPostEntity: SeenPost): ModularResult<Unit> {
        return safeRun {
            return@safeRun seenPostDao.insert(
                    SeenPostMapper.toEntity(seenPostEntity)
            )
        }
    }

    suspend fun selectAllByLoadableUid(loadableUid: String): ModularResult<List<SeenPost>> {
        return safeRun {
            return@safeRun seenPostDao.selectAllByLoadableUid(loadableUid)
                    .mapNotNull { seenPostEntity -> SeenPostMapper.fromEntity(seenPostEntity) }
        }
    }

    suspend fun deleteOlderThanOneMonth(): ModularResult<Int> {
        return safeRun {
            return@safeRun seenPostDao.deleteOlderThan(ONE_MONTH_AGO)
        }
    }

    companion object {
        private val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}