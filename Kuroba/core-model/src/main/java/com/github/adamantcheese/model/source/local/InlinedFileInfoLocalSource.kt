package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.mapper.InlinedFileInfoMapper
import com.github.adamantcheese.model.util.ensureBackgroundThread
import org.joda.time.DateTime

open class InlinedFileInfoLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag InlinedFileInfoLocalSource"
    private val inlinedFileInfoDao = database.inlinedFileDao()

    open suspend fun insert(inlinedFileInfo: InlinedFileInfo): ModularResult<Unit> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun inlinedFileInfoDao.insert(
                    InlinedFileInfoMapper.toEntity(
                            inlinedFileInfo,
                            DateTime.now()
                    )
            ).also { result ->
                logger.log(TAG, "insert(${inlinedFileInfo}) -> $result")
            }
        }
    }

    open suspend fun selectByFileUrl(fileUrl: String): ModularResult<InlinedFileInfo?> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun InlinedFileInfoMapper.fromEntity(inlinedFileInfoDao.selectByFileUrl(fileUrl))
                    .also { result -> logger.log(TAG, "selectByFileUrl(${fileUrl}) -> $result") }
        }
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_WEEK_AGO): ModularResult<Int> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun inlinedFileInfoDao.deleteOlderThan(dateTime)
                    .also { result -> logger.log(TAG, "deleteOlderThan($dateTime) -> $result") }
        }
    }

    open suspend fun deleteAll(): ModularResult<Int> {
        ensureBackgroundThread()

        return safeRun {
            return@safeRun inlinedFileInfoDao.deleteAll()
                    .also { result -> logger.log(TAG, "deleteAll() -> $result") }
        }
    }

    companion object {
        val ONE_WEEK_AGO = DateTime.now().minusWeeks(1)
    }
}