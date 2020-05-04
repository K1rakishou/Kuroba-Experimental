package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.model.data.video_service.MediaServiceType
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import java.util.concurrent.atomic.AtomicBoolean

class MediaServiceLinkExtraContentRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val cache: GenericCacheSource<String, MediaServiceLinkExtraContent>,
  private val mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
  private val mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag MediaServiceLinkExtraContentRepository"
  private val alreadyExecuted = AtomicBoolean(false)

  suspend fun getLinkExtraContent(
    mediaServiceType: MediaServiceType,
    requestUrl: String,
    videoId: String
  ): ModularResult<MediaServiceLinkExtraContent> {
    return repoGenericGetAction(
      cleanupFunc = { mediaServiceLinkExtraContentRepositoryCleanup().ignore() },
      getFromCacheFunc = { cache.get(videoId) },
      getFromLocalSourceFunc = {
        tryWithTransaction {
          mediaServiceLinkExtraContentLocalSource.selectByVideoId(videoId)
        }
      },
      getFromRemoteSourceFunc = {
        mediaServiceLinkExtraContentRemoteSource.fetchFromNetwork(
          requestUrl,
          mediaServiceType
        ).mapValue {
          MediaServiceLinkExtraContent(
            videoId,
            mediaServiceType,
            it.videoTitle,
            it.videoDuration
          )
        }
      },
      storeIntoCacheFunc = { mediaServiceLinkExtraContent ->
        cache.store(requestUrl, mediaServiceLinkExtraContent)
      },
      storeIntoLocalSourceFunc = { mediaServiceLinkExtraContent ->
        tryWithTransaction {
          mediaServiceLinkExtraContentLocalSource.insert(mediaServiceLinkExtraContent)
        }
      },
      tag = TAG
    )
  }

  suspend fun isCached(videoId: String): ModularResult<Boolean> {
    return tryWithTransaction {
      val hasInCache = cache.contains(videoId)
      if (hasInCache) {
        return@tryWithTransaction true
      }

      return@tryWithTransaction mediaServiceLinkExtraContentLocalSource.selectByVideoId(videoId) != null
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    return tryWithTransaction {
      return@tryWithTransaction mediaServiceLinkExtraContentLocalSource.deleteAll()
    }
  }

  suspend fun count(): ModularResult<Int> {
    return tryWithTransaction {
      return@tryWithTransaction mediaServiceLinkExtraContentLocalSource.count()
    }
  }

  private suspend fun mediaServiceLinkExtraContentRepositoryCleanup(): ModularResult<Int> {
    return tryWithTransaction {
      if (!alreadyExecuted.compareAndSet(false, true)) {
        return@tryWithTransaction 0
      }

      return@tryWithTransaction mediaServiceLinkExtraContentLocalSource.deleteOlderThan(
        MediaServiceLinkExtraContentLocalSource.ONE_WEEK_AGO
      )
    }
  }

}