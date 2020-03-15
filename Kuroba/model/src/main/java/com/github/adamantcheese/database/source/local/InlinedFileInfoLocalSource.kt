package com.github.adamantcheese.database.source.local

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.base.ModularResult.Companion.safeRun
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.InlinedFileInfo
import com.github.adamantcheese.database.mapper.InlinedFileInfoMapper
import org.joda.time.DateTime

class InlinedFileInfoLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag InlinedFileInfoLocalSource"
    private val inlinedFileInfoDao = database.inlinedFileDao()

    suspend fun insert(inlinedFileInfo: InlinedFileInfo): ModularResult<Unit> {
        logger.log(TAG, "insert(${inlinedFileInfo})")

        return safeRun {
            return@safeRun inlinedFileInfoDao.insert(
                    InlinedFileInfoMapper.toEntity(
                            inlinedFileInfo,
                            DateTime.now()
                    )
            )
        }
    }

    suspend fun selectByFileUrl(fileUrl: String): ModularResult<InlinedFileInfo?> {
        logger.log(TAG, "selectByFileUrl(${fileUrl})")

        return safeRun {
            return@safeRun InlinedFileInfoMapper.fromEntity(
                    inlinedFileInfoDao.selectByFileUrl(fileUrl)
            )
        }
    }

    suspend fun deleteOlderThanOneWeek(): ModularResult<Int> {
        logger.log(TAG, "deleteOlderThanOneWeek()")

        return safeRun {
            return@safeRun inlinedFileInfoDao.deleteOlderThan(ONE_WEEK_AGO)
        }
    }

    companion object {
        private val ONE_WEEK_AGO = DateTime.now().minusWeeks(1)
    }
}