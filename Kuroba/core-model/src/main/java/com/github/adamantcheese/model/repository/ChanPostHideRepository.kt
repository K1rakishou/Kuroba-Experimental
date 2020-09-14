package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostHide
import com.github.adamantcheese.model.source.local.ChanPostHideLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanPostHideRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanPostHideLocalSource
) : AbstractRepository(database, logger) {

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<List<ChanPostHide>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.preloadForThread(threadDescriptor)
      }
    }
  }

  suspend fun preloadForCatalog(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): ModularResult<List<ChanPostHide>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.preloadForCatalog(catalogDescriptor, count)
      }
    }
  }

  suspend fun createMany(chanPostHideList: List<ChanPostHide>): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.createMany(chanPostHideList)
      }
    }
  }

  suspend fun getTotalCount(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.getTotalCount()
      }
    }
  }

  suspend fun removeMany(postDescriptorList: List<PostDescriptor>): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.removeMany(postDescriptorList)
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.deleteAll()
      }
    }
  }

}