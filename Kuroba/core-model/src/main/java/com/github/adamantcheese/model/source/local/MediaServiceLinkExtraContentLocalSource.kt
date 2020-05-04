package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.model.mapper.MediaServiceLinkExtraContentMapper
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

  open suspend fun selectByVideoId(videoId: String): MediaServiceLinkExtraContent? {
    ensureInTransaction()

    return MediaServiceLinkExtraContentMapper.fromEntity(
      mediaServiceLinkExtraContentDao.selectByVideoId(videoId)
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