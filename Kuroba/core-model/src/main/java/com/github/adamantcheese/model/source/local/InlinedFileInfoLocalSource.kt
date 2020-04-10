package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.mapper.InlinedFileInfoMapper
import org.joda.time.DateTime

open class InlinedFileInfoLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag InlinedFileInfoLocalSource"
    private val inlinedFileInfoDao = database.inlinedFileDao()

    open suspend fun insert(inlinedFileInfo: InlinedFileInfo) {
        ensureInTransaction()

        return inlinedFileInfoDao.insert(
                InlinedFileInfoMapper.toEntity(
                        inlinedFileInfo,
                        DateTime.now()
                )
        )
    }

    open suspend fun selectByFileUrl(fileUrl: String): InlinedFileInfo? {
        ensureInTransaction()

        return InlinedFileInfoMapper.fromEntity(inlinedFileInfoDao.selectByFileUrl(fileUrl))
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_WEEK_AGO): Int {
        ensureInTransaction()

        return inlinedFileInfoDao.deleteOlderThan(dateTime)
    }

    open suspend fun deleteAll(): Int {
        ensureInTransaction()

        return inlinedFileInfoDao.deleteAll()
    }

    companion object {
        val ONE_WEEK_AGO = DateTime.now().minusWeeks(1)
    }
}