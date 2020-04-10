package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.CatalogDescriptor
import com.github.adamantcheese.model.data.SeenPost
import com.github.adamantcheese.model.mapper.SeenPostMapper
import org.joda.time.DateTime

open class SeenPostLocalSource(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger
) : AbstractLocalSource(database) {
    private val TAG = "$loggerTag SeenPostLocalSource"
    private val seenPostDao = database.seenPostDao()
    private val chanCatalogDao = database.chanCatalogDao()

    open suspend fun insert(seenPost: SeenPost) {
        ensureInTransaction()

        val chanCatalogEntity = chanCatalogDao.insert(
                seenPost.catalogDescriptor.siteName,
                seenPost.catalogDescriptor.boardCode
        )

        seenPostDao.insert(SeenPostMapper.toEntity(chanCatalogEntity.boardId, seenPost))
    }

    open suspend fun selectAllByCatalogDescriptor(catalogDescriptor: CatalogDescriptor): List<SeenPost> {
        ensureInTransaction()

        val chanCatalogEntity = chanCatalogDao.select(
                catalogDescriptor.siteName,
                catalogDescriptor.boardCode
        )

        if (chanCatalogEntity == null) {
            return emptyList()
        }

        return seenPostDao.selectAllByBoardId(chanCatalogEntity.boardId)
                .mapNotNull { seenPostEntity ->
                    return@mapNotNull SeenPostMapper.fromEntity(catalogDescriptor, seenPostEntity)
                }
    }

    open suspend fun deleteOlderThan(dateTime: DateTime = ONE_MONTH_AGO): Int {
        ensureInTransaction()

        return seenPostDao.deleteOlderThan(dateTime)

    }

    open suspend fun deleteAll(): Int {
        ensureInTransaction()

        return seenPostDao.deleteAll()
    }

    companion object {
        val ONE_MONTH_AGO = DateTime.now().minusMonths(1)
    }
}