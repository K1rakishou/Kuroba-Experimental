package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.Loadable2
import com.github.adamantcheese.database.mapper.Loadable2Mapper
import com.github.adamantcheese.database.util.ensureBackgroundThread

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