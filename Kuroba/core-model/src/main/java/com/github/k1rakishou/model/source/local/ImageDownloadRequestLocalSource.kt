package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.entity.download.ImageDownloadRequestEntity
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import org.joda.time.DateTime
import org.joda.time.Period

class ImageDownloadRequestLocalSource(
  database: KurobaDatabase,
) : AbstractLocalSource(database) {
  private val imageDownloadRequestDao = database.imageDownloadRequestDao()

  suspend fun createMany(imageDownloadRequests: List<ImageDownloadRequest>): List<ImageDownloadRequest> {
    ensureInTransaction()

    if (imageDownloadRequests.isEmpty()) {
      return emptyList()
    }

    val imageUrls = imageDownloadRequests.map { imageDownloadRequest -> imageDownloadRequest.imageFullUrl }

    val activeRequests = imageDownloadRequestDao.selectMany(imageUrls)
      .filter { imageDownloadRequestEntity -> imageDownloadRequestEntity.isQueued() }

    val newImageDownloadRequestEntities = imageDownloadRequests.mapNotNull { imageDownloadRequest ->
      val contains = activeRequests.any { activeRequest ->
        // If an image has QUEUED status and we are trying to download it again, then skip it. All
        // other statuses should be overwritten
        return@any activeRequest.imageServerFileName == imageDownloadRequest.imageServerFileName
          || activeRequest.imageFullUrl == imageDownloadRequest.imageFullUrl
      }

      if (contains) {
        return@mapNotNull null
      }

      return@mapNotNull ImageDownloadRequestEntity(
        uniqueId = imageDownloadRequest.uniqueId,
        imageServerFileName = imageDownloadRequest.imageServerFileName,
        imageFullUrl = imageDownloadRequest.imageFullUrl,
        newFileName = imageDownloadRequest.newFileName,
        status = imageDownloadRequest.status.rawValue,
        duplicatePathUri = imageDownloadRequest.duplicatePathUri,
        duplicatesResolution = imageDownloadRequest.duplicatesResolution.rawValue,
        createdOn = DateTime.now()
      )
    }

    imageDownloadRequestDao.createMany(newImageDownloadRequestEntities)

    return newImageDownloadRequestEntities.mapNotNull { newRequest ->
      val status = ImageDownloadRequest.Status.fromRawValue(newRequest.status)
        ?: return@mapNotNull null
      val resolution = ImageSaverV2Options.DuplicatesResolution.fromRawValue(newRequest.duplicatesResolution)
        ?: return@mapNotNull null

      return@mapNotNull ImageDownloadRequest(
        uniqueId = newRequest.uniqueId,
        imageServerFileName = newRequest.imageServerFileName,
        imageFullUrl = newRequest.imageFullUrl,
        newFileName = newRequest.newFileName,
        status = status,
        duplicatePathUri = newRequest.duplicatePathUri,
        duplicatesResolution = resolution,
        createdOn = newRequest.createdOn
      )
    }
  }

  suspend fun selectMany(uniqueId: String): List<ImageDownloadRequest> {
    ensureInTransaction()

    return imageDownloadRequestDao.selectMany(uniqueId)
      .mapNotNull { imageDownloadRequestEntity ->
        val status = ImageDownloadRequest.Status.fromRawValue(imageDownloadRequestEntity.status)
          ?: return@mapNotNull null
        val resolution = ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageDownloadRequestEntity.duplicatesResolution)
          ?: return@mapNotNull null

        return@mapNotNull ImageDownloadRequest(
          uniqueId = imageDownloadRequestEntity.uniqueId,
          imageServerFileName = imageDownloadRequestEntity.imageServerFileName,
          imageFullUrl = imageDownloadRequestEntity.imageFullUrl,
          newFileName = imageDownloadRequestEntity.newFileName,
          status = status,
          duplicatePathUri = imageDownloadRequestEntity.duplicatePathUri,
          duplicatesResolution = resolution,
          createdOn = imageDownloadRequestEntity.createdOn
        )
      }
  }

  suspend fun updateMany(imageDownloadRequests: List<ImageDownloadRequest>) {
    ensureInTransaction()

    val imageDownloadRequestEntities = imageDownloadRequests.map { imageDownloadRequest ->
      return@map ImageDownloadRequestEntity(
        uniqueId = imageDownloadRequest.uniqueId,
        imageServerFileName = imageDownloadRequest.imageServerFileName,
        imageFullUrl = imageDownloadRequest.imageFullUrl,
        newFileName = imageDownloadRequest.newFileName,
        status = imageDownloadRequest.status.rawValue,
        duplicatePathUri = imageDownloadRequest.duplicatePathUri,
        duplicatesResolution = imageDownloadRequest.duplicatesResolution.rawValue,
        createdOn = DateTime.now()
      )
    }

    imageDownloadRequestDao.updateMany(imageDownloadRequestEntities)
  }

  suspend fun deleteByUniqueId(uniqueId: String) {
    ensureInTransaction()

    imageDownloadRequestDao.deleteByUniqueId(uniqueId)
  }

  suspend fun deleteOld() {
    ensureInTransaction()

    imageDownloadRequestDao.deleteOlderThan(MONTH_AGO)
  }

  companion object {
    private val MONTH_AGO = DateTime.now().minus(Period.days(30))
  }
}