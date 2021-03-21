package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceLinkExtraContent
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.source.cache.GenericSuspendableCacheSource
import com.github.k1rakishou.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.k1rakishou.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

class MediaServiceLinkExtraContentRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val cache: GenericSuspendableCacheSource<MediaServiceKey, MediaServiceLinkExtraContent>,
  private val mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
  private val mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource
) : AbstractRepository(database) {
  private val TAG = "MediaServiceLinkExtraContentRepository"
  private val alreadyExecuted = AtomicBoolean(false)

  suspend fun getLinkExtraContent(
    mediaServiceType: MediaServiceType,
    requestUrl: String,
    videoId: GenericVideoId
  ): ModularResult<MediaServiceLinkExtraContent> {
    ensureBackgroundThread()
    val mediaServiceKey = MediaServiceKey(videoId, mediaServiceType)

    return applicationScope.myAsync {
      return@myAsync repoGenericGetAction(
        fileUrl = requestUrl,
        cleanupFunc = { mediaServiceLinkExtraContentRepositoryCleanup().ignore() },
        getFromCacheFunc = { cache.get(mediaServiceKey) },
        getFromLocalSourceFunc = {
          tryWithTransaction {
            mediaServiceLinkExtraContentLocalSource.selectByMediaServiceKey(
              videoId,
              mediaServiceKey
            )
          }
        },
        getFromRemoteSourceFunc = {
          mediaServiceLinkExtraContentRemoteSource.fetchFromNetwork(
            requestUrl,
            videoId,
            mediaServiceType
          ).mapValue {
            return@mapValue MediaServiceLinkExtraContent(
              videoId,
              mediaServiceType,
              it.videoTitle,
              it.videoDuration
            )
          }
        },
        storeIntoCacheFunc = { mediaServiceLinkExtraContent ->
          cache.store(mediaServiceKey, mediaServiceLinkExtraContent)
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

  suspend fun isCached(videoId: GenericVideoId, mediaServiceType: MediaServiceType): ModularResult<Boolean> {
    ensureBackgroundThread()
    val mediaServiceKey = MediaServiceKey(videoId, mediaServiceType)

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val hasInCache = cache.contains(mediaServiceKey)
        if (hasInCache) {
          return@tryWithTransaction true
        }

        val linkContent = mediaServiceLinkExtraContentLocalSource.selectByMediaServiceKey(
          videoId,
          mediaServiceKey
        )

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

  data class MediaServiceKey(
    val videoId: GenericVideoId,
    val mediaServiceType: MediaServiceType
  )

}