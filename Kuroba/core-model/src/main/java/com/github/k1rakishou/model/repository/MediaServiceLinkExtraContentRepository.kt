package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.video_service.MediaServiceLinkExtraContent
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.source.cache.GenericCacheSource
import com.github.k1rakishou.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.k1rakishou.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

class MediaServiceLinkExtraContentRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
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
    return applicationScope.myAsync {
      return@myAsync repoGenericGetAction(
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
          if (mediaServiceLinkExtraContent.isValid()) {
            tryWithTransaction {
              mediaServiceLinkExtraContentLocalSource.insert(mediaServiceLinkExtraContent)
            }
          } else {
            ModularResult.value(Unit)
          }
        },
        tag = TAG
      )
    }
  }

  suspend fun isCached(videoId: String): ModularResult<Boolean> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val hasInCache = cache.contains(videoId)
        if (hasInCache) {
          return@tryWithTransaction true
        }

        val linkContent = mediaServiceLinkExtraContentLocalSource.selectByVideoId(videoId)
        return@tryWithTransaction linkContent != null
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction mediaServiceLinkExtraContentLocalSource.deleteAll()
      }
    }
  }

  suspend fun count(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction mediaServiceLinkExtraContentLocalSource.count()
      }
    }
  }

  private suspend fun mediaServiceLinkExtraContentRepositoryCleanup(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        if (!alreadyExecuted.compareAndSet(false, true)) {
          return@tryWithTransaction 0
        }

        return@tryWithTransaction mediaServiceLinkExtraContentLocalSource.deleteOlderThan(
          MediaServiceLinkExtraContentLocalSource.ONE_WEEK_AGO
        )
      }
    }
  }

}