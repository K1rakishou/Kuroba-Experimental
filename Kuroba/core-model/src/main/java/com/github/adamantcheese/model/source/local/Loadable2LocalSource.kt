package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.Loadable2
import com.github.adamantcheese.model.mapper.Loadable2Mapper
import com.github.adamantcheese.model.util.ensureBackgroundThread

class Loadable2LocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag Loadable2LocalSource"
    private val loadableDao = database.loadableDao()

    suspend fun insert(loadable2: Loadable2): ModularResult<Unit> {
        logger.log(TAG, "insert($loadable2)")
        ensureBackgroundThread()

        return safeRun {
            return@safeRun loadableDao.insert(
                    Loadable2Mapper.toEntity(loadable2)
            )
        }
    }

    suspend fun selectByThreadUid(threadUid: String): ModularResult<Loadable2?> {
        logger.log(TAG, "selectByThreadUid($threadUid)")
        ensureBackgroundThread()

        return safeRun {
            return@safeRun Loadable2Mapper.fromEntity(
                    loadableDao.selectByThreadUid(threadUid)
            )
        }
    }

    suspend fun selectBySiteBoardOpId(
            siteName: String,
            boardCode: String,
            opId: Long
    ): ModularResult<Loadable2?> {
        logger.log(TAG, "selectBySiteBoardOpId($siteName, $boardCode, $opId)")
        ensureBackgroundThread()

        return safeRun {
            return@safeRun Loadable2Mapper.fromEntity(
                    loadableDao.selectBySiteBoardOpId(siteName, boardCode, opId)
            )
        }
    }

}