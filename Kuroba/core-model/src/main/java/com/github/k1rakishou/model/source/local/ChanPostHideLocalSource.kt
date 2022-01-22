package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.mapper.ChanPostHideMapper

class ChanPostHideLocalSource(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
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

  suspend fun createOrUpdateMany(chanPostHideList: Collection<ChanPostHide>) {
    ensureInTransaction()

    val entities = chanPostHideList.map { chanPostHide -> ChanPostHideMapper.toEntity(chanPostHide) }
    chanPostHideDao.insertManyOrUpdate(entities)
  }

  suspend fun removeMany(postDescriptorList: Collection<PostDescriptor>) {
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