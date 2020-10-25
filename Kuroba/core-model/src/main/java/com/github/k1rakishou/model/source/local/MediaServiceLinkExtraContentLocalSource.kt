package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceLinkExtraContent
import com.github.k1rakishou.model.mapper.MediaServiceLinkExtraContentMapper
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import org.joda.time.DateTime

open class MediaServiceLinkExtraContentLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val logger: Logger
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag MediaServiceLinkExtraContentLocalSource"
  private val mediaServiceLinkExtraContentDao = database.mediaServiceLinkExtraContentDao()

  open suspend fun insert(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent) {
    ensureInTransaction()

    return mediaServiceLinkExtraContentDao.insert(
      MediaServiceLinkExtraContentMapper.toEntity(
        mediaServiceLinkExtraContent,
        DateTime.now()
      )
    )
  }

  open suspend fun selectByMediaServiceKey(
    videoId: GenericVideoId,
    mediaServiceKey: MediaServiceLinkExtraContentRepository.MediaServiceKey
  ): MediaServiceLinkExtraContent? {
    ensureInTransaction()

    return MediaServiceLinkExtraContentMapper.fromEntity(
      videoId,
      mediaServiceLinkExtraContentDao.select(
        mediaServiceKey.videoId.id,
        mediaServiceKey.mediaServiceType
      )
    )
  }

  open suspend fun deleteOlderThan(dateTime: DateTime = ONE_WEEK_AGO): Int {
    ensureInTransaction()

    return mediaServiceLinkExtraContentDao.deleteOlderThan(dateTime)
  }

  open suspend fun deleteAll(): Int {
    ensureInTransaction()

    return mediaServiceLinkExtraContentDao.deleteAll()
  }

  suspend fun count(): Int {
    ensureInTransaction()

    return mediaServiceLinkExtraContentDao.count()
  }

  companion object {
    val ONE_WEEK_AGO = DateTime.now().minusWeeks(1)
  }
}