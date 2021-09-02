package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.catalog.CompositeCatalog
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.entity.chan.catalog.CompositeCatalogEntity

class CompositeCatalogLocalSource(
  database: KurobaDatabase
) : AbstractLocalSource(database) {
  private val compositeCatalogDao = database.compositeCatalogDao()

  suspend fun maxOrder(): Int {
    ensureInTransaction()

    return compositeCatalogDao.maxOrder() ?: 0
  }

  suspend fun loadAll(): List<CompositeCatalog> {
    ensureInTransaction()

    return compositeCatalogDao.selectAll()
      .mapNotNull { compositeCatalogEntity ->
        val compositeCatalogDescriptor = ChanDescriptor.CompositeCatalogDescriptor.deserializeFromString(
          compositeCatalogEntity.compositeBoardsString
        ) ?: return@mapNotNull null

        return@mapNotNull CompositeCatalog(
          name = compositeCatalogEntity.name,
          compositeCatalogDescriptor = compositeCatalogDescriptor
        )
      }
  }

  suspend fun createOrUpdate(compositeCatalog: CompositeCatalog, order: Int) {
    ensureInTransaction()

    val entity = CompositeCatalogEntity(
      name = compositeCatalog.name,
      compositeBoardsString = compositeCatalog.compositeCatalogDescriptor.serializeToString(),
      order = order
    )

    compositeCatalogDao.insert(entity)
  }

  suspend fun move(
    fromCompositeCatalog: CompositeCatalog,
    toCompositeCatalog: CompositeCatalog
  ): Boolean {
    ensureInTransaction()

    val fromEntity = compositeCatalogDao.selectByCompositeBoardsString(
      fromCompositeCatalog.compositeCatalogDescriptor.serializeToString()
    ) ?: return false
    val toEntity = compositeCatalogDao.selectByCompositeBoardsString(
      toCompositeCatalog.compositeCatalogDescriptor.serializeToString()
    ) ?: return false

    val order = fromEntity.order
    fromEntity.order = toEntity.order
    toEntity.order = order

    compositeCatalogDao.update(fromEntity)
    compositeCatalogDao.update(toEntity)

    return true
  }

  suspend fun delete(compositeCatalog: CompositeCatalog) {
    ensureInTransaction()

    compositeCatalogDao.delete(compositeCatalog.compositeCatalogDescriptor.serializeToString())
  }

}