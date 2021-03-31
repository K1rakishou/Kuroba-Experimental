package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.source.local.ImageDownloadRequestLocalSource
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

class ImageDownloadRequestRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val imageDownloadRequestLocalSource: ImageDownloadRequestLocalSource
) : AbstractRepository(database) {
  private val deletionRoutineExecuted = AtomicBoolean(false)

  suspend fun create(imageDownloadRequest: ImageDownloadRequest): ModularResult<ImageDownloadRequest?> {
    return createMany(listOf(imageDownloadRequest))
      .mapValue { imageDownloadRequests -> imageDownloadRequests.firstOrNull() }
  }

  suspend fun createMany(
    imageDownloadRequests: List<ImageDownloadRequest>
  ): ModularResult<List<ImageDownloadRequest>> {
    check(imageDownloadRequests.isNotEmpty()) { "imageDownloadRequests is empty" }

    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        if (deletionRoutineExecuted.compareAndSet(false, true)) {
          imageDownloadRequestLocalSource.deleteOldAndHangedInQueueStatus()
        }

        return@tryWithTransaction imageDownloadRequestLocalSource.createMany(imageDownloadRequests)
      }
    }
  }

  suspend fun selectMany(uniqueId: String): ModularResult<List<ImageDownloadRequest>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction imageDownloadRequestLocalSource.selectMany(uniqueId)
      }
    }
  }

  suspend fun selectManyWithStatus(
    uniqueId: String,
    downloadStatuses: Collection<ImageDownloadRequest.Status>
  ): ModularResult<List<ImageDownloadRequest>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction imageDownloadRequestLocalSource.selectManyWithStatus(
          uniqueId,
          downloadStatuses
        )
      }
    }
  }

  suspend fun completeMany(imageDownloadRequests: List<ImageDownloadRequest>): ModularResult<Unit> {
    if (imageDownloadRequests.isEmpty()) {
      return ModularResult.value(Unit)
    }

    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction imageDownloadRequestLocalSource.completeMany(imageDownloadRequests)
      }
    }
  }

  suspend fun updateMany(imageDownloadRequests: List<ImageDownloadRequest>): ModularResult<Unit> {
    if (imageDownloadRequests.isEmpty()) {
      return ModularResult.value(Unit)
    }

    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction imageDownloadRequestLocalSource.updateMany(imageDownloadRequests)
      }
    }
  }

  suspend fun deleteByUniqueId(uniqueId: String): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction imageDownloadRequestLocalSource.deleteByUniqueId(uniqueId)
      }
    }
  }

}