package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostHide
import com.github.adamantcheese.model.mapper.ChanPostHideMapper

class ChanPostHideLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger
) : AbstractLocalSource(database) {
  private val chanPostHideDao = database.chanPostHideDao()

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanPostHide> {
    ensureInTransaction()

    return chanPostHideDao.selectAllInThread(
      threadDescriptor.boardDescriptor.siteName(),
      threadDescriptor.boardDescriptor.boardCode,
      threadDescriptor.threadNo
    ).map { chanPostHideEntity -> ChanPostHideMapper.fromEntity(chanPostHideEntity) }
  }

  suspend fun preloadForCatalog(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): List<ChanPostHide> {
    ensureInTransaction()

    return chanPostHideDao.selectLatestForCatalog(
      catalogDescriptor.siteName(),
      catalogDescriptor.boardCode(),
      count
    ).map { chanPostHideEntity -> ChanPostHideMapper.fromEntity(chanPostHideEntity) }
  }

  suspend fun createMany(chanPostHideList: List<ChanPostHide>) {
    ensureInTransaction()

    val entities = chanPostHideList.map { chanPostHide -> ChanPostHideMapper.toEntity(chanPostHide) }
    chanPostHideDao.insertManyOrIgnore(entities)
  }

  suspend fun removeMany(postDescriptorList: List<PostDescriptor>) {
    ensureInTransaction()

    postDescriptorList.forEach { postDescriptor ->
      chanPostHideDao.delete(
        siteName = postDescriptor.boardDescriptor().siteName(),
        boardCode = postDescriptor.boardDescriptor().boardCode,
        threadNo = postDescriptor.threadDescriptor().threadNo,
        postNo = postDescriptor.postNo,
        postSubNo = postDescriptor.postSubNo
      )
    }
  }

  suspend fun getTotalCount(): Int {
    ensureInTransaction()

    return chanPostHideDao.totalCount()
  }

  suspend fun deleteAll() {
    ensureInTransaction()

    chanPostHideDao.deleteAll()
  }
}