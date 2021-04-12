package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.entity.download.ImageDownloadRequestEntity
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import okhttp3.HttpUrl
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

    val imageUrls = imageDownloadRequests
      .map { imageDownloadRequest -> imageDownloadRequest.imageFullUrl }

    val activeRequests = imageUrls
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> imageDownloadRequestDao.selectMany(chunk) }
      .filter { imageDownloadRequestEntity -> imageDownloadRequestEntity.isQueued() }

    val newImageDownloadRequestEntities = imageDownloadRequests.mapNotNull { imageDownloadRequest ->
      val contains = activeRequests.any { activeRequest ->
        // If an image has QUEUED status and we are trying to download it again, then skip it. All
        // other statuses should be overwritten
        return@any activeRequest.imageFullUrl == imageDownloadRequest.imageFullUrl
      }

      if (contains) {
        return@mapNotNull null
      }

      return@mapNotNull ImageDownloadRequestEntity(
        uniqueId = imageDownloadRequest.uniqueId,
        imageFullUrl = imageDownloadRequest.imageFullUrl,
        postDescriptorString = imageDownloadRequest.postDescriptorString,
        newFileName = imageDownloadRequest.newFileName,
        status = imageDownloadRequest.status.rawValue,
        duplicateFileUri = imageDownloadRequest.duplicateFileUri,
        duplicatesResolution = imageDownloadRequest.duplicatesResolution.rawValue,
        createdOn = DateTime.now()
      )
    }

    imageDownloadRequestDao.createMany(newImageDownloadRequestEntities)

    return newImageDownloadRequestEntities.mapNotNull { newRequest ->
      val status = ImageDownloadRequest.Status.fromRawValue(newRequest.status)
        ?: return@mapNotNull null
      val resolution = ImageSaverV2Options.DuplicatesResolution.fromRawValue(newRequest.duplicatesResolution)

      return@mapNotNull ImageDownloadRequest(
        uniqueId = newRequest.uniqueId,
        imageFullUrl = newRequest.imageFullUrl,
        postDescriptorString = newRequest.postDescriptorString,
        newFileName = newRequest.newFileName,
        status = status,
        duplicateFileUri = newRequest.duplicateFileUri,
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
        val resolution =
          ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageDownloadRequestEntity.duplicatesResolution)

        return@mapNotNull ImageDownloadRequest(
          uniqueId = imageDownloadRequestEntity.uniqueId,
          imageFullUrl = imageDownloadRequestEntity.imageFullUrl,
          postDescriptorString = imageDownloadRequestEntity.postDescriptorString,
          newFileName = imageDownloadRequestEntity.newFileName,
          status = status,
          duplicateFileUri = imageDownloadRequestEntity.duplicateFileUri,
          duplicatesResolution = resolution,
          createdOn = imageDownloadRequestEntity.createdOn
        )
      }
  }

  suspend fun selectManyWithStatus(
    uniqueId: String,
    downloadStatuses: Collection<ImageDownloadRequest.Status>
  ): List<ImageDownloadRequest> {
    ensureInTransaction()

    val rawDownloadStatuses = downloadStatuses.map { downloadStatus -> downloadStatus.rawValue }

    return imageDownloadRequestDao.selectManyWithStatus(uniqueId, rawDownloadStatuses)
      .mapNotNull { imageDownloadRequestEntity ->
        val status = ImageDownloadRequest.Status.fromRawValue(imageDownloadRequestEntity.status)
          ?: return@mapNotNull null
        val resolution =
          ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageDownloadRequestEntity.duplicatesResolution)

        return@mapNotNull ImageDownloadRequest(
          uniqueId = imageDownloadRequestEntity.uniqueId,
          imageFullUrl = imageDownloadRequestEntity.imageFullUrl,
          postDescriptorString = imageDownloadRequestEntity.postDescriptorString,
          newFileName = imageDownloadRequestEntity.newFileName,
          status = status,
          duplicateFileUri = imageDownloadRequestEntity.duplicateFileUri,
          duplicatesResolution = resolution,
          createdOn = imageDownloadRequestEntity.createdOn
        )
      }
  }

  suspend fun completeMany(imageDownloadRequests: List<ImageDownloadRequest>) {
    ensureInTransaction()

    val toDelete = mutableListOf<HttpUrl>()
    val toUpdate = mutableListOf<ImageDownloadRequestEntity>()

    imageDownloadRequests.forEach { imageDownloadRequest ->
      if (imageDownloadRequest.status == ImageDownloadRequest.Status.Downloaded) {
        toDelete += imageDownloadRequest.imageFullUrl
        return@forEach
      }

      toUpdate += ImageDownloadRequestEntity(
        uniqueId = imageDownloadRequest.uniqueId,
        imageFullUrl = imageDownloadRequest.imageFullUrl,
        postDescriptorString = imageDownloadRequest.postDescriptorString,
        newFileName = imageDownloadRequest.newFileName,
        status = imageDownloadRequest.status.rawValue,
        duplicateFileUri = imageDownloadRequest.duplicateFileUri,
        duplicatesResolution = imageDownloadRequest.duplicatesResolution.rawValue,
        createdOn = DateTime.now()
      )
    }

    imageDownloadRequestDao.updateMany(toUpdate)

    toDelete
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk -> imageDownloadRequestDao.deleteManyByUrl(chunk) }
  }

  suspend fun updateMany(imageDownloadRequests: List<ImageDownloadRequest>) {
    ensureInTransaction()

    imageDownloadRequests
      .map { imageDownloadRequest ->
        return@map ImageDownloadRequestEntity(
          uniqueId = imageDownloadRequest.uniqueId,
          imageFullUrl = imageDownloadRequest.imageFullUrl,
          postDescriptorString = imageDownloadRequest.postDescriptorString,
          newFileName = imageDownloadRequest.newFileName,
          status = imageDownloadRequest.status.rawValue,
          duplicateFileUri = imageDownloadRequest.duplicateFileUri,
          duplicatesResolution = imageDownloadRequest.duplicatesResolution.rawValue,
          createdOn = imageDownloadRequest.createdOn,
        )
      }
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk -> imageDownloadRequestDao.updateMany(chunk) }
  }

  suspend fun deleteByUniqueId(uniqueId: String) {
    ensureInTransaction()

    imageDownloadRequestDao.deleteByUniqueId(uniqueId)
  }

  suspend fun deleteOldAndHangedInQueueStatus() {
    ensureInTransaction()

    imageDownloadRequestDao.deleteOlderThan(DAY_AGO)
    imageDownloadRequestDao.deleteWithStatus(ImageDownloadRequest.Status.Queued.rawValue)
  }

  companion object {
    private val DAY_AGO = DateTime.now().minus(Period.days(1))
  }
}