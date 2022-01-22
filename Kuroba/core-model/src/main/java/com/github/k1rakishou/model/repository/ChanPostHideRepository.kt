package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostHide
import com.github.k1rakishou.model.source.local.ChanPostHideLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanPostHideRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanPostHideLocalSource
) : AbstractRepository(database) {

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<List<ChanPostHide>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.preloadForThread(threadDescriptor)
      }
    }
  }

  suspend fun preloadForCatalog(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): ModularResult<List<ChanPostHide>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.preloadForCatalog(catalogDescriptor, count)
      }
    }
  }

  suspend fun createOrUpdateMany(chanPostHideList: Collection<ChanPostHide>): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.createOrUpdateMany(chanPostHideList)
      }
    }
  }

  suspend fun getTotalCount(): ModularResult<Int> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.getTotalCount()
      }
    }
  }

  suspend fun removeMany(postDescriptorList: Collection<PostDescriptor>): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.removeMany(postDescriptorList)
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.deleteAll()
      }
    }
  }

}