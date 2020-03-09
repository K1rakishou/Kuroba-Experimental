package com.github.adamantcheese.database.source

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.dto.Loadable2
import com.github.adamantcheese.database.mapper.Loadable2Mapper

class Loadable2LocalSource(
        private val database: KurobaDatabase
) : AbstractLocalSource() {
    private val loadableDao = database.loadableDao()

    suspend fun insert(loadable2: Loadable2): ModularResult<Unit> {
        return safeRun {
            return@safeRun loadableDao.insert(
                    Loadable2Mapper.toEntity(loadable2)
            )
        }
    }

    suspend fun selectByThreadUid(threadUid: String): ModularResult<Loadable2?> {
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
        return safeRun {
            return@safeRun Loadable2Mapper.fromEntity(
                    loadableDao.selectBySiteBoardOpId(siteName, boardCode, opId)
            )
        }
    }

}